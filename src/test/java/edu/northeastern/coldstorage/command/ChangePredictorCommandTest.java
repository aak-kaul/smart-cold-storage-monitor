package edu.northeastern.coldstorage.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.logging.AuditEntry;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.ml.LinearRegressionPredictor;
import edu.northeastern.coldstorage.ml.MovingAveragePredictor;
import edu.northeastern.coldstorage.ml.PredictionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangePredictorCommandTest {

    private PredictionService service;
    private CommandInvoker invoker;
    private AuditLogger audit;

    @BeforeEach
    void setUp() {
        service = new PredictionService();
        audit = AuditLogger.getInstance();
        invoker = new CommandInvoker(audit);
    }

    @Test
    void switchesThePredictionModel() {
        MovingAveragePredictor average = new MovingAveragePredictor(4);
        String result = invoker.execute("tester", new ChangePredictorCommand(service, average));

        assertSame(average, service.getPredictor());
        assertEquals("Prediction model changed from Linear regression to Moving average (4)", result);
    }

    @Test
    void changeIsRecordedInAuditLogAndHistory() {
        invoker.execute("web", new ChangePredictorCommand(service, new MovingAveragePredictor()));

        List<AuditEntry> entries = audit.getEntries();
        AuditEntry last = entries.get(entries.size() - 1);
        assertEquals(AuditLogger.OPERATOR_ACTION, last.getEventType());
        assertTrue(last.getDetails().startsWith("Change prediction model by web"), last.getDetails());
        assertTrue(invoker.getHistory().get(0).startsWith("Change prediction model by web"));
        assertTrue(audit.verifyIntegrity());
    }

    @Test
    void canSwitchBack() {
        LinearRegressionPredictor regression = new LinearRegressionPredictor();
        invoker.execute("desktop", new ChangePredictorCommand(service, new MovingAveragePredictor()));
        invoker.execute("desktop", new ChangePredictorCommand(service, regression));
        assertSame(regression, service.getPredictor());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new ChangePredictorCommand(null, new LinearRegressionPredictor()));
        assertThrows(NullPointerException.class, () -> new ChangePredictorCommand(service, null));
    }
}

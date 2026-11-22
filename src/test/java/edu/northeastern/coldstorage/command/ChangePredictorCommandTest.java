package edu.northeastern.coldstorage.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.logging.AuditEntry;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.ml.LinearRegressionPredictor;
import edu.northeastern.coldstorage.ml.MovingAveragePredictor;
import edu.northeastern.coldstorage.ml.PredictionModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangePredictorCommandTest {

    private MonitorController controller;
    private CommandInvoker invoker;
    private AuditLogger audit;

    @BeforeEach
    void setUp() {
        controller = new MonitorController(new ComplianceChecker());
        audit = AuditLogger.getInstance();
        invoker = new CommandInvoker(audit);
    }

    @Test
    void switchesThePredictionModel() {
        String result = invoker.execute("tester",
                new ChangePredictorCommand(controller, PredictionModel.LINEAR_REGRESSION));

        assertInstanceOf(LinearRegressionPredictor.class, controller.getPredictor());
        assertEquals("Prediction model set to Linear regression", result);
    }

    @Test
    void changeIsRecordedInAuditLogAndHistory() {
        invoker.execute("web", new ChangePredictorCommand(controller, PredictionModel.MOVING_AVERAGE));

        List<AuditEntry> entries = audit.getEntries();
        AuditEntry last = entries.get(entries.size() - 1);
        assertEquals(AuditLogger.OPERATOR_ACTION, last.getEventType());
        assertTrue(last.getDetails().startsWith("Change prediction model by web"), last.getDetails());
        assertTrue(invoker.getHistory().get(0).startsWith("Change prediction model by web"));
        assertTrue(audit.verifyIntegrity());
    }

    @Test
    void canSwitchBack() {
        invoker.execute("desktop", new ChangePredictorCommand(controller, PredictionModel.LINEAR_REGRESSION));
        invoker.execute("desktop", new ChangePredictorCommand(controller, PredictionModel.MOVING_AVERAGE));
        assertInstanceOf(MovingAveragePredictor.class, controller.getPredictor());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new ChangePredictorCommand(null, PredictionModel.LINEAR_REGRESSION));
        assertThrows(NullPointerException.class, () -> new ChangePredictorCommand(controller, null));
    }
}

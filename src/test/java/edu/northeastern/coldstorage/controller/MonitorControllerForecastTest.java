package edu.northeastern.coldstorage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.command.ChangePredictorCommand;
import edu.northeastern.coldstorage.command.CommandInvoker;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.ml.LinearRegressionPredictor;
import edu.northeastern.coldstorage.ml.MovingAveragePredictor;
import edu.northeastern.coldstorage.ml.PredictionModel;
import edu.northeastern.coldstorage.ml.TemperatureForecaster;
import edu.northeastern.coldstorage.model.Forecast;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorControllerForecastTest {

    private MonitorController controller;

    @BeforeEach
    void setUp() {
        ComplianceChecker checker = new ComplianceChecker();
        // Linear regression makes the "rising room" scenario easy to reason about.
        controller = new MonitorController(checker,
                new TemperatureForecaster(checker, new LinearRegressionPredictor(), 3));
    }

    @Test
    void reportIncludesAForecastPerRoomWithEnoughHistory() {
        feed("A", 2.5, 3.0, 3.5, 4.0); // rising -> predicted unsafe
        feed("B", 3.0, 3.0, 3.0);      // flat -> predicted safe
        feed("C", 3.0);                // not enough history yet

        MonitoringReport report = controller.buildReport();
        List<Forecast> forecasts = report.getForecasts();

        assertEquals(2, forecasts.size());
        assertEquals("A", forecasts.get(0).roomId());
        assertTrue(forecasts.get(0).isRiskPredicted());
        assertEquals("B", forecasts.get(1).roomId());
        assertEquals(3.0, forecasts.get(1).predictedTemperature(), 1e-9);
    }

    @Test
    void predictorCanBeChangedOnTheController() {
        feed("A", 2.5, 3.0, 3.5, 4.0);
        controller.setPredictor(new MovingAveragePredictor());

        assertEquals("Moving average", controller.forecastAll().get(0).modelName());
        assertEquals("Moving average", controller.getPredictor().name());
    }

    @Test
    void changePredictorCommandSwitchesModelAndIsAudited() {
        AuditLogger audit = AuditLogger.getInstance();
        int before = audit.getEntries().size();

        String result = new CommandInvoker(audit).execute("desktop",
                new ChangePredictorCommand(controller, PredictionModel.MOVING_AVERAGE));

        assertEquals("Prediction model set to Moving average", result);
        assertTrue(controller.getPredictor() instanceof MovingAveragePredictor);
        assertEquals(before + 1, audit.getEntries().size());
    }

    private void feed(String room, double... temperatures) {
        for (int i = 0; i < temperatures.length; i++) {
            controller.process(new SensorReading(i, room,
                    LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i), temperatures[i], 85.0));
        }
    }
}

package edu.northeastern.coldstorage.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.ml.LinearRegressionPredictor;
import edu.northeastern.coldstorage.ml.TemperatureForecaster;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Tests the forecast section added to the console summary in Phase 3. */
class ConsoleAlertViewForecastTest {

    @Test
    void summaryShowsForecastAndRiskWarning() {
        ComplianceChecker checker = new ComplianceChecker();
        MonitorController controller = new MonitorController(checker,
                new TemperatureForecaster(checker, new LinearRegressionPredictor(), 3));
        double[] rising = {2.5, 3.0, 3.5, 4.0};
        for (int i = 0; i < rising.length; i++) {
            controller.process(new SensorReading(i, "Cold_Storage_A",
                    LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i), rising[i], 80.0));
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ConsoleAlertView(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0)
                .showReport(controller.buildReport());
        String printed = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(printed.contains("FORECAST (Linear regression, 3 readings ahead)"));
        assertTrue(printed.contains("Cold_Storage_A       5.50 C  NON-COMPLIANT"));
        assertTrue(printed.contains("spoilage risk predicted"));
    }

    @Test
    void noForecastSectionWithoutEnoughHistory() {
        MonitorController controller = new MonitorController(new ComplianceChecker(),
                new TemperatureForecaster(new ComplianceChecker(), new LinearRegressionPredictor(), 3));
        controller.process(new SensorReading(1, "Cold_Storage_A", LocalDateTime.of(2024, 1, 1, 0, 0), 3.0, 80.0));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ConsoleAlertView(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0)
                .showReport(controller.buildReport());

        assertFalse(buffer.toString(StandardCharsets.UTF_8).contains("FORECAST"));
    }
}

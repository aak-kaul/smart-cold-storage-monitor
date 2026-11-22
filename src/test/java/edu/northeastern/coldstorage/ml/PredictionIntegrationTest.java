package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.data.ColdStorageDataReader;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Kaggle dataset through the monitoring pipeline and the prediction
 * service together, the same way {@code Main} does.
 */
class PredictionIntegrationTest {

    private static List<SensorReading> readings;
    private static Map<String, List<Double>> temperaturesByRoom;

    @BeforeAll
    static void loadDataset() throws IOException {
        readings = new ColdStorageDataReader().readFromResource("IoT.csv");
        temperaturesByRoom = new TreeMap<>();
        for (SensorReading r : readings) {
            temperaturesByRoom.computeIfAbsent(r.getRoomId(), id -> new ArrayList<>()).add(r.getTemperature());
        }
    }

    @Test
    void everyMonitoredRoomGetsAFinitePredictionFromBothStrategies() {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        PredictionService predictions = new PredictionService();
        for (SensorReading r : readings) {
            controller.process(r);
            predictions.record(r);
        }

        assertEquals(3, predictions.getRoomIds().size());
        for (String room : predictions.getRoomIds()) {
            assertTrue(controller.getUnit(room) != null, "room should also be monitored: " + room);
            assertEquals(PredictionService.DEFAULT_HISTORY_SIZE, predictions.getHistoryCount(room));
            for (TemperaturePredictor model
                    : List.of(new LinearRegressionPredictor(), new MovingAveragePredictor())) {
                predictions.setPredictor(model);
                double next = predictions.predict(room, 1).getAsDouble();
                assertTrue(Double.isFinite(next), model.getName() + " gave " + next + " for " + room);
                assertTrue(predictions.predictStatus(room, 1).isPresent());
            }
        }
    }

    @Test
    void backtestErrorOnRealDataIsReasonable() {
        for (Map.Entry<String, List<Double>> room : temperaturesByRoom.entrySet()) {
            for (TemperaturePredictor model
                    : List.of(new LinearRegressionPredictor(), new MovingAveragePredictor())) {
                double mae = PredictionService.meanAbsoluteError(
                        model, room.getValue(), PredictionService.DEFAULT_HISTORY_SIZE);
                // Readings range from about -4 to 19 C; a useful model should be off by
                // only a few degrees on average.
                assertTrue(mae >= 0 && mae < 3.0,
                        model.getName() + " MAE for " + room.getKey() + " was " + mae);
            }
        }
    }
}

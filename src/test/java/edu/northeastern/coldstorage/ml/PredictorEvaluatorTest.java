package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.data.ColdStorageDataReader;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictorEvaluatorTest {

    @Test
    void perfectLineGivesZeroErrorForLinearRegression() {
        List<Double> line = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            line.add(2.0 + 0.1 * i);
        }
        assertEquals(0.0, PredictorEvaluator.meanAbsoluteError(new LinearRegressionPredictor(), line, 3), 1e-9);
    }

    @Test
    void movingAverageErrorOnAKnownSeries() {
        // Window 1 just predicts "same as last reading". Series 1,2,3,4 with 1 step ahead:
        // predictions 1,2,3 vs actual 2,3,4 -> every error is 1.
        double mae = PredictorEvaluator.meanAbsoluteError(new MovingAveragePredictor(1),
                List.of(1.0, 2.0, 3.0, 4.0), 1);
        assertEquals(1.0, mae, 1e-9);
    }

    @Test
    void roomsAreEvaluatedSeparatelyThenAveraged() {
        List<SensorReading> readings = new ArrayList<>();
        // Room A rises by 1 (error 1 each), room B is flat (error 0) -> average 0.5
        for (int i = 0; i < 4; i++) {
            readings.add(reading("A", i, 1.0 + i));
            readings.add(reading("B", i, 3.0));
        }
        double mae = PredictorEvaluator.meanAbsoluteErrorByRoom(new MovingAveragePredictor(1), readings, 1);
        assertEquals(0.5, mae, 1e-9);
    }

    @Test
    void bothModelsGiveSensibleErrorsOnTheRealDataset() throws IOException {
        List<SensorReading> readings = new ColdStorageDataReader().readFromResource("IoT.csv");
        double linear = PredictorEvaluator.meanAbsoluteErrorByRoom(new LinearRegressionPredictor(), readings, 3);
        double average = PredictorEvaluator.meanAbsoluteErrorByRoom(new MovingAveragePredictor(), readings, 3);
        assertTrue(linear > 0 && linear < 5, "linear MAE " + linear);
        assertTrue(average > 0 && average < 5, "moving average MAE " + average);
    }

    @Test
    void tooShortSeriesIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PredictorEvaluator.meanAbsoluteError(new LinearRegressionPredictor(), List.of(1.0, 2.0), 3));
    }

    private static SensorReading reading(String room, int i, double temp) {
        return new SensorReading(i, room, LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i), temp, 80.0);
    }
}

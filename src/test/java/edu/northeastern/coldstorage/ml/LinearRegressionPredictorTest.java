package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinearRegressionPredictorTest {

    private static final double EPS = 1e-9;
    private final LinearRegressionPredictor predictor = new LinearRegressionPredictor();

    @Test
    void risingTrendIsExtended() {
        // 2.0, 2.5, 3.0, 3.5 rises by 0.5 per reading
        List<Double> history = List.of(2.0, 2.5, 3.0, 3.5);
        assertEquals(4.0, predictor.predict(history, 1), EPS);
        assertEquals(5.0, predictor.predict(history, 3), EPS);
    }

    @Test
    void fallingTrendIsExtended() {
        List<Double> history = List.of(6.0, 5.0, 4.0);
        assertEquals(3.0, predictor.predict(history, 1), EPS);
    }

    @Test
    void flatHistoryPredictsSameValue() {
        assertEquals(3.2, predictor.predict(List.of(3.2, 3.2, 3.2, 3.2), 5), EPS);
    }

    @Test
    void singleReadingPredictsThatReading() {
        assertEquals(4.4, predictor.predict(List.of(4.4), 2), EPS);
    }

    @Test
    void noisyDataFollowsBestFitLine() {
        // Least-squares line through (0,1), (1,3), (2,2), (3,4): slope 0.8, intercept 1.3
        // Prediction at x = 4: 1.3 + 0.8 * 4 = 4.5
        assertEquals(4.5, predictor.predict(List.of(1.0, 3.0, 2.0, 4.0), 1), EPS);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(List.of(3.0), 0));
    }
}

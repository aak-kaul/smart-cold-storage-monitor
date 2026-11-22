package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinearRegressionPredictorTest {

    private final LinearRegressionPredictor predictor = new LinearRegressionPredictor();

    @Test
    void extendsAPerfectRisingLine() {
        // 1, 2, 3, 4 rises by 1 per reading: the next is 5, three ahead is 7.
        List<Double> history = List.of(1.0, 2.0, 3.0, 4.0);
        assertEquals(5.0, predictor.predict(history, 1), 1e-9);
        assertEquals(7.0, predictor.predict(history, 3), 1e-9);
    }

    @Test
    void extendsAFallingLine() {
        assertEquals(1.0, predictor.predict(List.of(4.0, 3.5, 3.0, 2.5, 2.0, 1.5), 1), 1e-9);
    }

    @Test
    void flatHistoryPredictsTheSameTemperature() {
        assertEquals(3.2, predictor.predict(List.of(3.2, 3.2, 3.2), 5), 1e-9);
    }

    @Test
    void fitsTheBestLineThroughNoisyData() {
        // Least squares through (0,1) (1,3) (2,2) (3,4): slope 0.8, intercept 1.3 -> x=4 gives 4.5
        assertEquals(4.5, predictor.predict(List.of(1.0, 3.0, 2.0, 4.0), 1), 1e-9);
    }

    @Test
    void usesOnlyTheMostRecentWindow() {
        LinearRegressionPredictor shortWindow = new LinearRegressionPredictor(3);
        // The old readings (100s) are outside the 3-reading window and must be ignored.
        assertEquals(4.0, shortWindow.predict(List.of(100.0, 100.0, 1.0, 2.0, 3.0), 1), 1e-9);
    }

    @Test
    void needsAtLeastTwoReadings() {
        assertEquals(2, predictor.minimumHistory());
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(List.of(3.0), 1));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new LinearRegressionPredictor(1));
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(List.of(1.0, 2.0), 0));
        assertThrows(NullPointerException.class, () -> predictor.predict(null, 1));
    }

    @Test
    void hasAReadableName() {
        assertEquals("Linear regression", predictor.name());
        assertEquals(LinearRegressionPredictor.DEFAULT_WINDOW, predictor.getWindow());
    }
}

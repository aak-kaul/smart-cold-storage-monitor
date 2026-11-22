package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MovingAveragePredictorTest {

    private static final double EPS = 1e-9;

    @Test
    void averagesOnlyTheLastWindowReadings() {
        MovingAveragePredictor predictor = new MovingAveragePredictor(3);
        // last three: 4.0, 5.0, 6.0
        assertEquals(5.0, predictor.predict(List.of(100.0, 4.0, 5.0, 6.0), 1), EPS);
    }

    @Test
    void shortHistoryAveragesEverything() {
        MovingAveragePredictor predictor = new MovingAveragePredictor(6);
        assertEquals(3.0, predictor.predict(List.of(2.0, 4.0), 1), EPS);
    }

    @Test
    void forecastIsSameForEveryHorizon() {
        MovingAveragePredictor predictor = new MovingAveragePredictor(2);
        List<Double> history = List.of(1.0, 2.0, 3.0);
        assertEquals(predictor.predict(history, 1), predictor.predict(history, 10), EPS);
    }

    @Test
    void singleSpikeIsSmoothedOut() {
        MovingAveragePredictor predictor = new MovingAveragePredictor(4);
        // one 11.0 spike among 3.0 readings: average is 5.0, not 11.0
        assertEquals(5.0, predictor.predict(List.of(3.0, 3.0, 3.0, 11.0), 1), EPS);
    }

    @Test
    void defaultWindowIsUsed() {
        assertEquals(MovingAveragePredictor.DEFAULT_WINDOW, new MovingAveragePredictor().getWindow());
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MovingAveragePredictor(0));
        MovingAveragePredictor predictor = new MovingAveragePredictor();
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(List.of(3.0), 0));
    }
}

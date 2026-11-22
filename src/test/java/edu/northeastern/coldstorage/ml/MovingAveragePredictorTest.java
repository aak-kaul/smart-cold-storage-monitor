package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MovingAveragePredictorTest {

    @Test
    void predictsTheAverageOfTheWindow() {
        MovingAveragePredictor predictor = new MovingAveragePredictor(3);
        // Only the last three (2, 4, 6) are averaged.
        assertEquals(4.0, predictor.predict(List.of(100.0, 2.0, 4.0, 6.0), 1), 1e-9);
    }

    @Test
    void usesAllReadingsWhenFewerThanTheWindow() {
        MovingAveragePredictor predictor = new MovingAveragePredictor(10);
        assertEquals(3.0, predictor.predict(List.of(2.0, 4.0), 1), 1e-9);
    }

    @Test
    void predictionDoesNotDependOnHowFarAhead() {
        MovingAveragePredictor predictor = new MovingAveragePredictor();
        List<Double> history = List.of(3.0, 3.5, 4.0);
        assertEquals(predictor.predict(history, 1), predictor.predict(history, 10), 1e-9);
    }

    @Test
    void worksWithASingleReading() {
        MovingAveragePredictor predictor = new MovingAveragePredictor();
        assertEquals(1, predictor.minimumHistory());
        assertEquals(3.7, predictor.predict(List.of(3.7), 1), 1e-9);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new MovingAveragePredictor(0));
        assertThrows(IllegalArgumentException.class, () -> new MovingAveragePredictor().predict(List.of(), 1));
    }
}

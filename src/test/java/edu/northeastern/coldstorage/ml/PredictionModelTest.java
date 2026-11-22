package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PredictionModelTest {

    @Test
    void createsTheMatchingPredictor() {
        assertTrue(PredictionModel.LINEAR_REGRESSION.create() instanceof LinearRegressionPredictor);
        assertTrue(PredictionModel.MOVING_AVERAGE.create() instanceof MovingAveragePredictor);
    }

    @Test
    void findsModelsByKeyOrDisplayName() {
        assertEquals(PredictionModel.LINEAR_REGRESSION, PredictionModel.fromName("linear"));
        assertEquals(PredictionModel.MOVING_AVERAGE, PredictionModel.fromName("Moving-Average"));
        assertEquals(PredictionModel.MOVING_AVERAGE, PredictionModel.fromName(" moving average "));
    }

    @Test
    void unknownNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PredictionModel.fromName("neural-net"));
    }
}

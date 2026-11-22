package edu.northeastern.coldstorage.ml;

import java.util.List;

/**
 * Strategy interface for temperature prediction.
 *
 * <p>Each implementation forecasts a future temperature from a room's recent history.
 * {@link PredictionService} depends only on this interface, so prediction models can be
 * swapped at runtime without changing the service or its callers.
 */
public interface TemperaturePredictor {

    /** Returns a short, human-readable name for this model, e.g. "Linear regression". */
    String getName();

    /**
     * Predicts the temperature a number of readings ahead.
     *
     * @param history    recent temperatures in time order, oldest first; must not be empty
     * @param stepsAhead how many readings into the future to predict; must be at least 1
     * @return the predicted temperature in degrees Celsius
     * @throws IllegalArgumentException if history is empty or stepsAhead is less than 1
     */
    double predict(List<Double> history, int stepsAhead);

    /** The fewest past temperatures this model needs; 1 unless a model says otherwise. */
    default int minimumHistory() {
        return 1;
    }

    /** Shared argument check for implementations. */
    static void validate(List<Double> history, int stepsAhead) {
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("history must contain at least one temperature");
        }
        if (stepsAhead < 1) {
            throw new IllegalArgumentException("stepsAhead must be at least 1");
        }
    }
}

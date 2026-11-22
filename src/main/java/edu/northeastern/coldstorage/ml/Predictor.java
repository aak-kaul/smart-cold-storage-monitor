package edu.northeastern.coldstorage.ml;

import java.util.List;

/**
 * A temperature prediction model (Strategy pattern).
 *
 * <p>The forecaster only depends on this interface, so models can be swapped at runtime
 * (for example from the desktop GUI) without changing any other code.
 */
public interface Predictor extends TemperaturePredictor {

    /** Human-readable model name, e.g. "Linear regression". */
    String name();

    /** Same as {@link #name()}, so every model also works with {@link PredictionService}. */
    @Override
    default String getName() {
        return name();
    }

    /** The fewest past temperatures this model needs to make a prediction. */
    @Override
    int minimumHistory();

    /**
     * Predicts a future temperature.
     *
     * @param history    past temperatures, oldest first (at least {@link #minimumHistory()} values)
     * @param stepsAhead how many readings into the future to predict (1 = the next reading)
     * @return the predicted temperature in °C
     * @throws IllegalArgumentException if there is too little history or stepsAhead is below 1
     */
    @Override
    double predict(List<Double> history, int stepsAhead);
}

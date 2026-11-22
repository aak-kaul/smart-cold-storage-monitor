package edu.northeastern.coldstorage.ml;

import java.util.List;

/**
 * Predicts that the temperature will equal the average of the last few readings.
 *
 * <p>This is a simple baseline model. It smooths out short spikes and is hard to fool
 * with noisy data, but it ignores trends, so its forecast is the same for every
 * {@code stepsAhead}.
 */
public final class MovingAveragePredictor implements TemperaturePredictor {

    /** Default number of recent readings to average. */
    public static final int DEFAULT_WINDOW = 6;

    private final int window;

    /** Creates a predictor that averages the last {@value #DEFAULT_WINDOW} readings. */
    public MovingAveragePredictor() {
        this(DEFAULT_WINDOW);
    }

    /**
     * Creates a predictor that averages the last {@code window} readings.
     *
     * @param window how many recent readings to average; must be at least 1
     * @throws IllegalArgumentException if window is less than 1
     */
    public MovingAveragePredictor(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1");
        }
        this.window = window;
    }

    @Override
    public String getName() {
        return "Moving average (" + window + ")";
    }

    @Override
    public double predict(List<Double> history, int stepsAhead) {
        TemperaturePredictor.validate(history, stepsAhead);
        int from = Math.max(0, history.size() - window);
        double sum = 0;
        for (int i = from; i < history.size(); i++) {
            sum += history.get(i);
        }
        return sum / (history.size() - from);
    }

    public int getWindow() {
        return window;
    }
}

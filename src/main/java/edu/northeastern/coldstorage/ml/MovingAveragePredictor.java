package edu.northeastern.coldstorage.ml;

import java.util.List;
import java.util.Objects;

/**
 * Predicts that the temperature will stay at the average of the most recent readings.
 *
 * <p>Simple and stable: it ignores trends, so it is less fooled by a single noisy reading.
 */
public class MovingAveragePredictor implements Predictor {

    /** Default number of recent readings that are averaged. */
    public static final int DEFAULT_WINDOW = 6;

    private final int window;

    /** Creates a predictor that averages the last {@value #DEFAULT_WINDOW} readings. */
    public MovingAveragePredictor() {
        this(DEFAULT_WINDOW);
    }

    /**
     * Creates a predictor with a custom window.
     *
     * @param window how many recent readings to average (at least 1)
     */
    public MovingAveragePredictor(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1");
        }
        this.window = window;
    }

    @Override
    public String name() {
        return "Moving average";
    }

    @Override
    public int minimumHistory() {
        return 1;
    }

    @Override
    public double predict(List<Double> history, int stepsAhead) {
        PredictorChecks.check(history, minimumHistory(), stepsAhead);
        List<Double> recent = history.subList(Math.max(0, history.size() - window), history.size());
        double sum = 0;
        for (double t : recent) {
            sum += t;
        }
        return sum / recent.size();
    }

    public int getWindow() {
        return window;
    }

    @Override
    public String toString() {
        return name() + " (last " + window + " readings)";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MovingAveragePredictor && ((MovingAveragePredictor) o).window == window;
    }

    @Override
    public int hashCode() {
        return Objects.hash("moving-average", window);
    }
}

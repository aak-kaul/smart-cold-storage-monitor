package edu.northeastern.coldstorage.ml;

import java.util.List;
import java.util.Objects;

/**
 * Predicts by fitting a straight trend line through the most recent temperatures
 * (ordinary least squares) and extending it into the future.
 *
 * <p>Good at spotting a room that is steadily warming up before it leaves the safe range.
 */
public class LinearRegressionPredictor implements Predictor {

    /** Default number of recent readings the trend line is fitted to. */
    public static final int DEFAULT_WINDOW = 12;

    private final int window;

    /** Creates a predictor that fits the last {@value #DEFAULT_WINDOW} readings. */
    public LinearRegressionPredictor() {
        this(DEFAULT_WINDOW);
    }

    /**
     * Creates a predictor with a custom window.
     *
     * @param window how many recent readings to fit (at least 2)
     */
    public LinearRegressionPredictor(int window) {
        if (window < 2) {
            throw new IllegalArgumentException("window must be at least 2");
        }
        this.window = window;
    }

    @Override
    public String name() {
        return "Linear regression";
    }

    @Override
    public int minimumHistory() {
        return 2;
    }

    @Override
    public double predict(List<Double> history, int stepsAhead) {
        PredictorChecks.check(history, minimumHistory(), stepsAhead);
        List<Double> recent = history.subList(Math.max(0, history.size() - window), history.size());
        int n = recent.size();

        // x is the reading's position (0, 1, 2, ...), y is its temperature.
        double meanX = (n - 1) / 2.0;
        double meanY = 0;
        for (double y : recent) {
            meanY += y;
        }
        meanY /= n;

        double covariance = 0;
        double varianceX = 0;
        for (int x = 0; x < n; x++) {
            covariance += (x - meanX) * (recent.get(x) - meanY);
            varianceX += (x - meanX) * (x - meanX);
        }
        double slope = covariance / varianceX;
        double intercept = meanY - slope * meanX;

        double futureX = (n - 1) + stepsAhead;
        return intercept + slope * futureX;
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
        return o instanceof LinearRegressionPredictor && ((LinearRegressionPredictor) o).window == window;
    }

    @Override
    public int hashCode() {
        return Objects.hash("linear", window);
    }
}

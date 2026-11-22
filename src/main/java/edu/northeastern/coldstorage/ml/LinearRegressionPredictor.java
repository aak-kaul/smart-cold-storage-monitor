package edu.northeastern.coldstorage.ml;

import java.util.List;

/**
 * Predicts temperature by fitting a straight line to the recent history
 * (ordinary least squares) and extending it into the future.
 *
 * <p>Readings are treated as evenly spaced: the i-th temperature is placed at x = i.
 * The fitted line is {@code y = intercept + slope * x}, and the prediction is the line's
 * value at {@code x = (n - 1) + stepsAhead}. This model reacts to trends, such as a
 * cooling unit that is slowly failing, which a plain average would miss.
 */
public final class LinearRegressionPredictor implements TemperaturePredictor {

    @Override
    public String getName() {
        return "Linear regression";
    }

    @Override
    public double predict(List<Double> history, int stepsAhead) {
        TemperaturePredictor.validate(history, stepsAhead);
        int n = history.size();
        if (n == 1) {
            // A single point has no trend, so the best guess is that it stays the same.
            return history.get(0);
        }

        double meanX = (n - 1) / 2.0;
        double meanY = 0;
        for (double y : history) {
            meanY += y;
        }
        meanY /= n;

        // slope = sum((x - meanX)(y - meanY)) / sum((x - meanX)^2)
        double covariance = 0;
        double varianceX = 0;
        for (int x = 0; x < n; x++) {
            double dx = x - meanX;
            covariance += dx * (history.get(x) - meanY);
            varianceX += dx * dx;
        }
        double slope = covariance / varianceX;
        double intercept = meanY - slope * meanX;

        return intercept + slope * ((n - 1) + stepsAhead);
    }
}

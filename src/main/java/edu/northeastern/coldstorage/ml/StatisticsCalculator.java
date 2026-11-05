package edu.northeastern.coldstorage.ml;

import java.util.List;
import java.util.Objects;

/**
 * Basic descriptive statistics over a list of values (e.g. temperatures).
 */
public final class StatisticsCalculator {

    private StatisticsCalculator() {
        // Utility class: not meant to be instantiated.
    }

    /** Returns the arithmetic mean. */
    public static double mean(List<Double> values) {
        requireNonEmpty(values);
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    /** Returns the population variance (average squared distance from the mean). */
    public static double variance(List<Double> values) {
        double mean = mean(values);
        double sumSquares = 0;
        for (double v : values) {
            sumSquares += (v - mean) * (v - mean);
        }
        return sumSquares / values.size();
    }

    /** Returns the population standard deviation. */
    public static double standardDeviation(List<Double> values) {
        return Math.sqrt(variance(values));
    }

    private static void requireNonEmpty(List<Double> values) {
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
    }
}
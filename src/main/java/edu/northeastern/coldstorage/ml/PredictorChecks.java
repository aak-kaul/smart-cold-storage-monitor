package edu.northeastern.coldstorage.ml;

import java.util.List;
import java.util.Objects;

/** Shared argument checks for {@link Predictor} implementations. */
final class PredictorChecks {

    private PredictorChecks() {
    }

    static void check(List<Double> history, int minimumHistory, int stepsAhead) {
        Objects.requireNonNull(history, "history must not be null");
        if (history.size() < minimumHistory) {
            throw new IllegalArgumentException(
                    "Need at least " + minimumHistory + " readings, got " + history.size());
        }
        if (stepsAhead < 1) {
            throw new IllegalArgumentException("stepsAhead must be at least 1");
        }
    }
}

package edu.northeastern.coldstorage.ml;

import java.util.Locale;

/**
 * The prediction models users can choose from (command line, desktop GUI, web dashboard).
 * Each value knows how to create its {@link Predictor} strategy.
 */
public enum PredictionModel {
    LINEAR_REGRESSION("linear", "Linear regression"),
    MOVING_AVERAGE("moving-average", "Moving average");

    private final String key;
    private final String displayName;

    PredictionModel(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /** Creates a new predictor for this model with its default settings. */
    public Predictor create() {
        switch (this) {
            case MOVING_AVERAGE:
                return new MovingAveragePredictor();
            case LINEAR_REGRESSION:
            default:
                return new LinearRegressionPredictor();
        }
    }

    /** Short name used on the command line, e.g. "linear". */
    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Finds a model by its key or display name, ignoring case.
     *
     * @throws IllegalArgumentException if nothing matches
     */
    public static PredictionModel fromName(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (PredictionModel model : values()) {
            if (model.key.equals(wanted) || model.displayName.toLowerCase(Locale.ROOT).equals(wanted)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown prediction model: " + name
                + " (use 'linear' or 'moving-average')");
    }

    @Override
    public String toString() {
        return displayName;
    }
}

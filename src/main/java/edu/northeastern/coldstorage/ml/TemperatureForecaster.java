package edu.northeastern.coldstorage.ml;

import edu.northeastern.coldstorage.model.Forecast;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Predicts each room's upcoming temperature and whether it will still be safe
 * (Functional Requirement 2: predict future temperatures; Objective: predict spoilage risk).
 *
 * <p>The actual math is delegated to a {@link Predictor} strategy that can be replaced
 * while the system is running.
 */
public class TemperatureForecaster {

    /** By default, forecast three readings ahead (three hours for the hourly IoT.csv data). */
    public static final int DEFAULT_STEPS_AHEAD = 3;

    private final ComplianceChecker checker;
    private final int stepsAhead;
    /** Volatile so a model switched from the GUI thread is seen by the sensor threads. */
    private volatile Predictor predictor;

    /**
     * Creates a forecaster using the moving-average model, three readings ahead.
     * Moving average is the default because it was the more accurate model on our dataset
     * (see {@link PredictorEvaluator}): about 0.98 °C average error vs 1.30 °C for linear regression.
     */
    public TemperatureForecaster(ComplianceChecker checker) {
        this(checker, new MovingAveragePredictor(), DEFAULT_STEPS_AHEAD);
    }

    /**
     * Creates a forecaster.
     *
     * @param checker    rules used to classify the predicted temperature
     * @param predictor  the prediction strategy
     * @param stepsAhead how many readings ahead to predict (at least 1)
     */
    public TemperatureForecaster(ComplianceChecker checker, Predictor predictor, int stepsAhead) {
        this.checker = Objects.requireNonNull(checker, "checker must not be null");
        this.predictor = Objects.requireNonNull(predictor, "predictor must not be null");
        if (stepsAhead < 1) {
            throw new IllegalArgumentException("stepsAhead must be at least 1");
        }
        this.stepsAhead = stepsAhead;
    }

    /**
     * Forecasts one room.
     *
     * @param unit the room, with its recent temperature history
     * @return the forecast, or empty if the room does not have enough history yet
     */
    public Optional<Forecast> forecast(StorageUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        Predictor current = predictor; // read once so a concurrent switch can't mix two models
        List<Double> history = unit.getRecentTemperatures();
        if (history.size() < current.minimumHistory()) {
            return Optional.empty();
        }
        double predicted = current.predict(history, stepsAhead);
        return Optional.of(new Forecast(unit.getRoomId(), predicted, checker.classify(predicted),
                current.name(), stepsAhead));
    }

    /** Replaces the prediction model (Strategy pattern). */
    public void setPredictor(Predictor predictor) {
        this.predictor = Objects.requireNonNull(predictor, "predictor must not be null");
    }

    public Predictor getPredictor() {
        return predictor;
    }

    public int getStepsAhead() {
        return stepsAhead;
    }
}

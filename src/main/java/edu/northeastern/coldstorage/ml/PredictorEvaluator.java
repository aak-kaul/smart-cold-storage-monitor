package edu.northeastern.coldstorage.ml;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Measures how accurate prediction models are on past data (backtesting), so the
 * team or an operator can choose the best {@link TemperaturePredictor} for a dataset.
 *
 * <p>For every reading after the first {@code historySize}, the model predicts it from the
 * {@code historySize} readings before it, and the absolute error is recorded. The score is
 * the mean absolute error (MAE) in degrees Celsius; lower is better.
 */
public class PredictorEvaluator {

    private final int historySize;

    /**
     * Creates an evaluator.
     *
     * @param historySize how many previous readings each prediction may use; at least 1
     * @throws IllegalArgumentException if historySize is less than 1
     */
    public PredictorEvaluator(int historySize) {
        if (historySize < 1) {
            throw new IllegalArgumentException("historySize must be at least 1");
        }
        this.historySize = historySize;
    }

    public int getHistorySize() {
        return historySize;
    }

    /**
     * Returns a model's mean absolute error on one series of temperatures.
     *
     * @param model        the model to evaluate
     * @param temperatures past temperatures in time order
     * @return the mean absolute error, or NaN if there are not enough temperatures
     */
    public double meanAbsoluteError(TemperaturePredictor model, List<Double> temperatures) {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(temperatures, "temperatures must not be null");
        double totalError = 0;
        int predictions = 0;
        for (int i = historySize; i < temperatures.size(); i++) {
            double predicted = model.predict(temperatures.subList(i - historySize, i), 1);
            totalError += Math.abs(predicted - temperatures.get(i));
            predictions++;
        }
        return predictions == 0 ? Double.NaN : totalError / predictions;
    }

    /**
     * Returns a model's mean absolute error averaged over several series (e.g. one per room).
     * Series that are too short to evaluate are skipped.
     *
     * @return the average error, or NaN if no series could be evaluated
     */
    public double averageError(TemperaturePredictor model, Collection<List<Double>> series) {
        Objects.requireNonNull(series, "series must not be null");
        double total = 0;
        int counted = 0;
        for (List<Double> temperatures : series) {
            double mae = meanAbsoluteError(model, temperatures);
            if (!Double.isNaN(mae)) {
                total += mae;
                counted++;
            }
        }
        return counted == 0 ? Double.NaN : total / counted;
    }

    /**
     * Scores every model on the same data.
     *
     * @return each model's average error, in the same order as {@code models}
     */
    public Map<TemperaturePredictor, Double> compare(List<TemperaturePredictor> models,
                                                     Collection<List<Double>> series) {
        Objects.requireNonNull(models, "models must not be null");
        Map<TemperaturePredictor, Double> scores = new LinkedHashMap<>();
        for (TemperaturePredictor model : models) {
            scores.put(model, averageError(model, series));
        }
        return scores;
    }

    /**
     * Returns the model with the lowest average error.
     *
     * @throws IllegalArgumentException if models is empty or no model could be evaluated
     */
    public TemperaturePredictor findBest(List<TemperaturePredictor> models, Collection<List<Double>> series) {
        TemperaturePredictor best = null;
        double bestError = Double.POSITIVE_INFINITY;
        for (Map.Entry<TemperaturePredictor, Double> score : compare(models, series).entrySet()) {
            if (!Double.isNaN(score.getValue()) && score.getValue() < bestError) {
                best = score.getKey();
                bestError = score.getValue();
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("no model could be evaluated on this data");
        }
        return best;
    }
}

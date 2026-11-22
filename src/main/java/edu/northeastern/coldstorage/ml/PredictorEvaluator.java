package edu.northeastern.coldstorage.ml;

import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Measures how accurate a prediction model is on real data, so we can compare models
 * with numbers instead of guessing.
 *
 * <p>It replays each room's readings in order ("walk-forward"): at every point it asks
 * the model to predict a reading it has not seen yet, then compares with the real value.
 * The result is the mean absolute error (MAE) in °C: lower is better.
 */
public final class PredictorEvaluator {

    private PredictorEvaluator() {
    }

    /**
     * Computes the walk-forward mean absolute error for one sequence of temperatures.
     *
     * @param predictor    the model to test
     * @param temperatures temperatures in time order
     * @param stepsAhead   how far ahead each prediction looks
     * @return the mean absolute error in °C
     * @throws IllegalArgumentException if the sequence is too short to make any prediction
     */
    public static double meanAbsoluteError(Predictor predictor, List<Double> temperatures, int stepsAhead) {
        Objects.requireNonNull(predictor, "predictor must not be null");
        Objects.requireNonNull(temperatures, "temperatures must not be null");
        double totalError = 0;
        int predictions = 0;
        // 'end' is how many readings the model may see; the target is stepsAhead readings later.
        for (int end = predictor.minimumHistory(); end - 1 + stepsAhead < temperatures.size(); end++) {
            // Only give the model the same amount of history a StorageUnit keeps.
            List<Double> history = temperatures.subList(Math.max(0, end - StorageUnit.HISTORY_SIZE), end);
            double predicted = predictor.predict(history, stepsAhead);
            double actual = temperatures.get(end - 1 + stepsAhead);
            totalError += Math.abs(predicted - actual);
            predictions++;
        }
        if (predictions == 0) {
            throw new IllegalArgumentException("Not enough temperatures to evaluate the model");
        }
        return totalError / predictions;
    }

    /**
     * Evaluates a model on a whole dataset, treating each room as its own time series
     * and averaging the rooms' errors.
     *
     * @param predictor  the model to test
     * @param readings   readings for one or more rooms, in time order
     * @param stepsAhead how far ahead each prediction looks
     * @return the average of the rooms' mean absolute errors, in °C
     */
    public static double meanAbsoluteErrorByRoom(Predictor predictor, List<SensorReading> readings,
                                                 int stepsAhead) {
        Map<String, List<Double>> byRoom = new LinkedHashMap<>();
        for (SensorReading r : readings) {
            byRoom.computeIfAbsent(r.getRoomId(), k -> new ArrayList<>()).add(r.getTemperature());
        }
        double sum = 0;
        for (List<Double> series : byRoom.values()) {
            sum += meanAbsoluteError(predictor, series, stepsAhead);
        }
        return sum / byRoom.size();
    }
}

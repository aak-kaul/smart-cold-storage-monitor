package edu.northeastern.coldstorage.ml;

import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Forecasts each storage room's temperature using an interchangeable
 * {@link TemperaturePredictor} (Strategy pattern: this class is the context).
 *
 * <p>The service keeps only the most recent {@code historySize} temperatures per room,
 * so memory stays bounded while sensors stream data. All public methods are
 * {@code synchronized} because readings may arrive from several sensor threads at once.
 */
public class PredictionService {

    /** Default number of recent readings kept per room (24 hourly readings = one day). */
    public static final int DEFAULT_HISTORY_SIZE = 24;

    private final int historySize;
    private final ComplianceChecker checker;
    private final Map<String, Deque<Double>> historyByRoom = new HashMap<>();
    private TemperaturePredictor predictor;

    /** Creates a service with linear regression, the default checker and a one-day history. */
    public PredictionService() {
        this(new LinearRegressionPredictor(), new ComplianceChecker(), DEFAULT_HISTORY_SIZE);
    }

    /**
     * Creates a prediction service.
     *
     * @param predictor   the prediction strategy to start with
     * @param checker     used to classify predicted temperatures
     * @param historySize how many recent readings to keep per room; must be at least 2
     * @throws IllegalArgumentException if historySize is less than 2
     */
    public PredictionService(TemperaturePredictor predictor, ComplianceChecker checker, int historySize) {
        this.predictor = Objects.requireNonNull(predictor, "predictor must not be null");
        this.checker = Objects.requireNonNull(checker, "checker must not be null");
        if (historySize < 2) {
            throw new IllegalArgumentException("historySize must be at least 2");
        }
        this.historySize = historySize;
    }

    /** Swaps the prediction strategy. Existing history is kept. */
    public synchronized void setPredictor(TemperaturePredictor predictor) {
        this.predictor = Objects.requireNonNull(predictor, "predictor must not be null");
    }

    public synchronized TemperaturePredictor getPredictor() {
        return predictor;
    }

    /** Adds a reading to its room's history, dropping the oldest one when the history is full. */
    public synchronized void record(SensorReading reading) {
        Objects.requireNonNull(reading, "reading must not be null");
        Deque<Double> history = historyByRoom.computeIfAbsent(reading.getRoomId(), id -> new ArrayDeque<>());
        if (history.size() == historySize) {
            history.removeFirst();
        }
        history.addLast(reading.getTemperature());
    }

    /** Adds every reading in order. */
    public void recordAll(List<SensorReading> readings) {
        for (SensorReading reading : readings) {
            record(reading);
        }
    }

    /**
     * Predicts a room's temperature a number of readings ahead.
     *
     * @return the prediction, or empty if the room has no readings yet
     * @throws IllegalArgumentException if stepsAhead is less than 1
     */
    public synchronized OptionalDouble predict(String roomId, int stepsAhead) {
        Deque<Double> history = historyByRoom.get(roomId);
        if (history == null || history.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(predictor.predict(new ArrayList<>(history), stepsAhead));
    }

    /**
     * Classifies the predicted temperature, so a room that is heading out of the safe
     * range can be flagged before it gets there.
     *
     * @return the predicted status, or empty if the room has no readings yet
     */
    public synchronized Optional<ComplianceStatus> predictStatus(String roomId, int stepsAhead) {
        OptionalDouble predicted = predict(roomId, stepsAhead);
        return predicted.isPresent()
                ? Optional.of(checker.classify(predicted.getAsDouble()))
                : Optional.empty();
    }

    /**
     * Forecasts a room's temperature directly from its {@link StorageUnit}, using the
     * unit's recent temperatures (at most {@code historySize} of them). This lets views such
     * as the GUI and web dashboard show a forecast for any unit in a {@code MonitoringReport}
     * without feeding readings into this service first.
     *
     * @return the prediction, or empty if the unit has no readings yet
     * @throws IllegalArgumentException if stepsAhead is less than 1
     */
    public synchronized OptionalDouble forecast(StorageUnit unit, int stepsAhead) {
        Objects.requireNonNull(unit, "unit must not be null");
        List<Double> recent = unit.getRecentTemperatures();
        if (recent.isEmpty()) {
            return OptionalDouble.empty();
        }
        List<Double> window = recent.subList(Math.max(0, recent.size() - historySize), recent.size());
        return OptionalDouble.of(predictor.predict(window, stepsAhead));
    }

    /**
     * Classifies a unit's forecast temperature, so a room heading out of the safe range
     * can be flagged before it gets there.
     *
     * @return the predicted status, or empty if the unit has no readings yet
     */
    public synchronized Optional<ComplianceStatus> forecastStatus(StorageUnit unit, int stepsAhead) {
        OptionalDouble predicted = forecast(unit, stepsAhead);
        return predicted.isPresent()
                ? Optional.of(checker.classify(predicted.getAsDouble()))
                : Optional.empty();
    }

    /** Returns the rooms that have at least one reading, in no particular order. */
    public synchronized List<String> getRoomIds() {
        return new ArrayList<>(historyByRoom.keySet());
    }

    /** Returns how many readings are currently kept for a room. */
    public synchronized int getHistoryCount(String roomId) {
        Deque<Double> history = historyByRoom.get(roomId);
        return history == null ? 0 : history.size();
    }

    public int getHistorySize() {
        return historySize;
    }

    /**
     * Measures how accurate a strategy is on a series of past temperatures (backtesting).
     *
     * <p>For every reading after the first {@code historySize}, the strategy predicts it
     * from the {@code historySize} readings before it, and the absolute error is recorded.
     * The result is the mean absolute error in degrees Celsius; lower is better.
     *
     * @param model        the strategy to evaluate
     * @param temperatures past temperatures in time order
     * @param historySize  how many previous readings each prediction may use; at least 1
     * @return the mean absolute error, or NaN if there are not enough temperatures
     */
    public static double meanAbsoluteError(TemperaturePredictor model, List<Double> temperatures,
                                           int historySize) {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(temperatures, "temperatures must not be null");
        if (historySize < 1) {
            throw new IllegalArgumentException("historySize must be at least 1");
        }
        double totalError = 0;
        int predictions = 0;
        for (int i = historySize; i < temperatures.size(); i++) {
            double predicted = model.predict(temperatures.subList(i - historySize, i), 1);
            totalError += Math.abs(predicted - temperatures.get(i));
            predictions++;
        }
        return predictions == 0 ? Double.NaN : totalError / predictions;
    }
}

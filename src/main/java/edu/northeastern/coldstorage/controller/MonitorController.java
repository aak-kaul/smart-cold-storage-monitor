package edu.northeastern.coldstorage.controller;

import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.ml.StatisticsCalculator;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Runs the core use case "Detect unsafe storage conditions":
 * receive a reading, store it, check it against the safe range, and
 * notify every registered {@link AlertListener} when it is unsafe (Observer pattern).
 */
public class MonitorController {

    private final ComplianceChecker checker;
    private final List<AlertListener> listeners = new ArrayList<>();
    private final Map<String, StorageUnit> units = new TreeMap<>();
    private final List<Double> temperatures = new ArrayList<>();
    private final Map<ComplianceStatus, Integer> statusCounts = new EnumMap<>(ComplianceStatus.class);

    /**
     * Creates a controller.
     *
     * @param checker the compliance rules to apply
     */
    public MonitorController(ComplianceChecker checker) {
        this.checker = Objects.requireNonNull(checker, "checker must not be null");
        for (ComplianceStatus s : ComplianceStatus.values()) {
            statusCounts.put(s, 0);
        }
    }

    /** Registers a listener to be notified of alerts. */
    public void addListener(AlertListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /** Stops notifying a listener. */
    public void removeListener(AlertListener listener) {
        listeners.remove(listener);
    }

    /**
     * Processes one reading: stores it, classifies it and raises an alert if needed.
     *
     * @param reading the incoming reading
     * @return the reading's compliance status
     */
    public ComplianceStatus process(SensorReading reading) {
        Objects.requireNonNull(reading, "reading must not be null");
        ComplianceStatus status = checker.classify(reading);

        units.computeIfAbsent(reading.getRoomId(), StorageUnit::new).record(reading, status);
        temperatures.add(reading.getTemperature());
        statusCounts.merge(status, 1, Integer::sum);

        if (status != ComplianceStatus.COMPLIANT) {
            notifyListeners(new Alert(reading, status));
        }
        return status;
    }

    /**
     * Processes a batch of readings in order and returns a summary report.
     *
     * @param readings the readings to process
     * @return a report covering every reading processed so far
     */
    public MonitoringReport processAll(List<SensorReading> readings) {
        Objects.requireNonNull(readings, "readings must not be null");
        for (SensorReading reading : readings) {
            process(reading);
        }
        return buildReport();
    }

    /**
     * Builds a report covering every reading processed so far.
     *
     * @throws IllegalStateException if no readings have been processed
     */
    public MonitoringReport buildReport() {
        if (temperatures.isEmpty()) {
            throw new IllegalStateException("No readings have been processed");
        }
        int total = temperatures.size();
        double percentNonCompliant = 100.0 * statusCounts.get(ComplianceStatus.NON_COMPLIANT) / total;
        return new MonitoringReport(
                total,
                statusCounts,
                StatisticsCalculator.mean(temperatures),
                StatisticsCalculator.variance(temperatures),
                percentNonCompliant,
                ComplianceChecker.DEFAULT_CONTRACTOR_THRESHOLD_PERCENT,
                new ArrayList<>(units.values()));
    }

    /** Returns the storage unit for a room, or null if no readings were seen for it. */
    public StorageUnit getUnit(String roomId) {
        return units.get(roomId);
    }

    public ComplianceChecker getChecker() {
        return checker;
    }

    private void notifyListeners(Alert alert) {
        for (AlertListener listener : listeners) {
            listener.onAlert(alert);
        }
    }
}
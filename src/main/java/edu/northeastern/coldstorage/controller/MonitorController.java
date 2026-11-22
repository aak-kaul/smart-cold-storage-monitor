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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the core use case "Detect unsafe storage conditions":
 * receive a reading, store it, check it against the safe range, and
 * notify every registered {@link AlertListener} when it is unsafe (Observer pattern).
 *
 * <p>Thread safety: several simulated sensors call {@link #process(SensorReading)} at the
 * same time, while the desktop GUI and web dashboard read reports from other threads.
 * Every method that touches shared state is {@code synchronized}, and reports contain
 * copies of the storage units, so readers never see half-updated data.
 */
public class MonitorController {

    private final ComplianceChecker checker;
    /** Copy-on-write so listeners can be added from any thread while alerts are being sent. */
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();
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
     * Safe to call from several sensor threads at once.
     *
     * @param reading the incoming reading
     * @return the reading's compliance status
     */
    public synchronized ComplianceStatus process(SensorReading reading) {
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

    /** Returns true once at least one reading has been processed. */
    public synchronized boolean hasReadings() {
        return !temperatures.isEmpty();
    }

    /** Returns how many readings have been processed so far. */
    public synchronized int getReadingCount() {
        return temperatures.size();
    }

    /**
     * Builds a report covering every reading processed so far. The report holds copies
     * of the storage units, so it stays consistent while new readings keep arriving.
     *
     * @throws IllegalStateException if no readings have been processed
     */
    public synchronized MonitoringReport buildReport() {
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
                copyUnits());
    }

    /**
     * Returns a copy of the storage unit for a room, or null if no readings were seen for it.
     * Changing the copy does not affect the controller.
     */
    public synchronized StorageUnit getUnit(String roomId) {
        StorageUnit unit = units.get(roomId);
        return unit == null ? null : unit.copy();
    }

    public ComplianceChecker getChecker() {
        return checker;
    }

    /** Copies every unit; callers must hold this object's lock. */
    private List<StorageUnit> copyUnits() {
        List<StorageUnit> copies = new ArrayList<>();
        for (StorageUnit unit : units.values()) {
            copies.add(unit.copy());
        }
        return copies;
    }

    private void notifyListeners(Alert alert) {
        for (AlertListener listener : listeners) {
            listener.onAlert(alert);
        }
    }
}

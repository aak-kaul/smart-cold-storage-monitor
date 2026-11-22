package edu.northeastern.coldstorage.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One cold storage room. Stores its latest state and running statistics.
 *
 * <p>The mean and variance are updated incrementally with Welford's algorithm,
 * so the unit never has to keep every reading in memory. Only the most recent
 * {@value #HISTORY_SIZE} temperatures are kept, for charts and temperature prediction.
 */
public final class StorageUnit {

    /** How many recent temperatures each room remembers (for charts and forecasting). */
    public static final int HISTORY_SIZE = 48;

    private final String roomId;
    private double currentTemperature;
    private ComplianceStatus currentStatus;
    private int readingCount;
    private double mean;
    private double sumSquaredDiffs;
    private double minTemperature = Double.POSITIVE_INFINITY;
    private double maxTemperature = Double.NEGATIVE_INFINITY;
    private final Map<ComplianceStatus, Integer> statusCounts = new EnumMap<>(ComplianceStatus.class);
    private final Deque<Double> recentTemperatures = new ArrayDeque<>();

    /**
     * Creates an empty storage unit.
     *
     * @param roomId the room identifier, e.g. "Cold_Storage_A"
     */
    public StorageUnit(String roomId) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        for (ComplianceStatus s : ComplianceStatus.values()) {
            statusCounts.put(s, 0);
        }
    }

    /**
     * Records a new reading and its compliance status.
     *
     * @param reading a reading for this room
     * @param status  the status the reading was classified as
     * @throws IllegalArgumentException if the reading belongs to a different room
     */
    public synchronized void record(SensorReading reading, ComplianceStatus status) {
        Objects.requireNonNull(reading, "reading must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (!reading.getRoomId().equals(roomId)) {
            throw new IllegalArgumentException(
                    "Reading for " + reading.getRoomId() + " recorded on unit " + roomId);
        }
        double t = reading.getTemperature();
        readingCount++;
        double delta = t - mean;
        mean += delta / readingCount;
        sumSquaredDiffs += delta * (t - mean);
        minTemperature = Math.min(minTemperature, t);
        maxTemperature = Math.max(maxTemperature, t);
        currentTemperature = t;
        currentStatus = status;
        statusCounts.merge(status, 1, Integer::sum);
        recentTemperatures.addLast(t);
        if (recentTemperatures.size() > HISTORY_SIZE) {
            recentTemperatures.removeFirst();
        }
    }

    /**
     * Returns an independent copy of this unit. The controller hands copies to the
     * views so a GUI or web thread never reads a unit while a sensor thread updates it.
     */
    public synchronized StorageUnit copy() {
        StorageUnit copy = new StorageUnit(roomId);
        copy.currentTemperature = currentTemperature;
        copy.currentStatus = currentStatus;
        copy.readingCount = readingCount;
        copy.mean = mean;
        copy.sumSquaredDiffs = sumSquaredDiffs;
        copy.minTemperature = minTemperature;
        copy.maxTemperature = maxTemperature;
        copy.statusCounts.putAll(statusCounts);
        copy.recentTemperatures.addAll(recentTemperatures);
        return copy;
    }

    /** Returns the most recent temperatures, oldest first (at most {@value #HISTORY_SIZE}). */
    public synchronized List<Double> getRecentTemperatures() {
        return new ArrayList<>(recentTemperatures);
    }

    public String getRoomId() {
        return roomId;
    }

    public synchronized int getReadingCount() {
        return readingCount;
    }

    /** Returns the most recent temperature, or NaN if no readings yet. */
    public synchronized double getCurrentTemperature() {
        return readingCount == 0 ? Double.NaN : currentTemperature;
    }

    /** Returns the most recent status, or null if no readings yet. */
    public synchronized ComplianceStatus getCurrentStatus() {
        return currentStatus;
    }

    /** Returns the running mean temperature, or NaN if no readings yet. */
    public synchronized double getMean() {
        return readingCount == 0 ? Double.NaN : mean;
    }

    /** Returns the running population variance, or NaN if no readings yet. */
    public synchronized double getVariance() {
        return readingCount == 0 ? Double.NaN : sumSquaredDiffs / readingCount;
    }

    /**
     * Returns the running population standard deviation, or NaN if no readings yet.
     * Tiny negative variances caused by floating-point rounding are treated as zero.
     */
    public synchronized double getStandardDeviation() {
        if (readingCount == 0) {
            return Double.NaN;
        }
        return Math.sqrt(Math.max(0.0, getVariance()));
    }

    public synchronized double getMinTemperature() {
        return readingCount == 0 ? Double.NaN : minTemperature;
    }

    public synchronized double getMaxTemperature() {
        return readingCount == 0 ? Double.NaN : maxTemperature;
    }

    /** Returns how many readings had the given status. */
    public synchronized int getCount(ComplianceStatus status) {
        return statusCounts.get(status);
    }
}
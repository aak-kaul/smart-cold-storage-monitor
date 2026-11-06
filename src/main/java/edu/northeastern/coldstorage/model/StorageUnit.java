package edu.northeastern.coldstorage.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One cold storage room. Stores its latest state and running statistics.
 *
 * <p>The mean and variance are updated incrementally with Welford's algorithm,
 * so the unit never has to keep every reading in memory.
 */
public final class StorageUnit {

    private final String roomId;
    private double currentTemperature;
    private ComplianceStatus currentStatus;
    private int readingCount;
    private double mean;
    private double sumSquaredDiffs;
    private double minTemperature = Double.POSITIVE_INFINITY;
    private double maxTemperature = Double.NEGATIVE_INFINITY;
    private final Map<ComplianceStatus, Integer> statusCounts = new EnumMap<>(ComplianceStatus.class);

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
    public void record(SensorReading reading, ComplianceStatus status) {
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
    }

    public String getRoomId() {
        return roomId;
    }

    public int getReadingCount() {
        return readingCount;
    }

    /** Returns the most recent temperature, or NaN if no readings yet. */
    public double getCurrentTemperature() {
        return readingCount == 0 ? Double.NaN : currentTemperature;
    }

    /** Returns the most recent status, or null if no readings yet. */
    public ComplianceStatus getCurrentStatus() {
        return currentStatus;
    }

    /** Returns the running mean temperature, or NaN if no readings yet. */
    public double getMean() {
        return readingCount == 0 ? Double.NaN : mean;
    }

    /** Returns the running population variance, or NaN if no readings yet. */
    public double getVariance() {
        return readingCount == 0 ? Double.NaN : sumSquaredDiffs / readingCount;
    }

    /**
     * Returns the running population standard deviation, or NaN if no readings yet.
     * Tiny negative variances caused by floating-point rounding are treated as zero.
     */
    public double getStandardDeviation() {
        if (readingCount == 0) {
            return Double.NaN;
        }
        return Math.sqrt(Math.max(0.0, getVariance()));
    }

    public double getMinTemperature() {
        return readingCount == 0 ? Double.NaN : minTemperature;
    }

    public double getMaxTemperature() {
        return readingCount == 0 ? Double.NaN : maxTemperature;
    }

    /** Returns how many readings had the given status. */
    public int getCount(ComplianceStatus status) {
        return statusCounts.get(status);
    }
}
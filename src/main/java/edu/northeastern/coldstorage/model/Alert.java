package edu.northeastern.coldstorage.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An alert raised when a reading is not compliant.
 *
 * <p>Every alert gets a unique id so an operator can acknowledge a specific one.
 * Alerts start unacknowledged; acknowledging is done through operator commands
 * (see {@code edu.northeastern.coldstorage.command}).
 */
public final class Alert {

    /** Shared counter so ids stay unique even when several sensor threads raise alerts at once. */
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private final long id;
    private final SensorReading reading;
    private final ComplianceStatus status;
    /** Volatile because the GUI/web threads acknowledge alerts that sensor threads created. */
    private volatile boolean acknowledged;

    /**
     * Creates an alert for a reading.
     *
     * @param reading the reading that triggered the alert
     * @param status  its compliance status (must not be COMPLIANT)
     * @throws IllegalArgumentException if status is COMPLIANT
     */
    public Alert(SensorReading reading, ComplianceStatus status) {
        this.reading = Objects.requireNonNull(reading, "reading must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (status == ComplianceStatus.COMPLIANT) {
            throw new IllegalArgumentException("A compliant reading does not raise an alert");
        }
        this.id = NEXT_ID.getAndIncrement();
        this.acknowledged = false;
    }

    /** Returns this alert's unique id. */
    public long getId() {
        return id;
    }

    public SensorReading getReading() {
        return reading;
    }

    public ComplianceStatus getStatus() {
        return status;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    /** Marks this alert as acknowledged by an operator. */
    public void acknowledge() {
        this.acknowledged = true;
    }

    /** Returns a one-line description suitable for logs and the console. */
    public String getMessage() {
        return String.format("[%s] %s at %s: %.2f C",
                status.getLabel(), reading.getRoomId(), reading.getTimestamp(), reading.getTemperature());
    }

    @Override
    public String toString() {
        return getMessage();
    }
}

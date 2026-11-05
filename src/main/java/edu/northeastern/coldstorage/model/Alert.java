package edu.northeastern.coldstorage.model;

import java.util.Objects;

/**
 * An alert raised when a reading is not compliant.
 *
 * <p>Alerts start unacknowledged; an operator can acknowledge them later
 * (this will be driven by Command objects in a future phase).
 */
public final class Alert {

    private final SensorReading reading;
    private final ComplianceStatus status;
    private boolean acknowledged;

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
        this.acknowledged = false;
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
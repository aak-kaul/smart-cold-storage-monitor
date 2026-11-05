package edu.northeastern.coldstorage.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One immutable reading from a cold storage sensor (one row of the dataset).
 */
public final class SensorReading {

    private final int id;
    private final String roomId;
    private final LocalDateTime timestamp;
    private final double temperature;
    private final double humidity;

    /**
     * Creates a sensor reading.
     *
     * @param id          the reading id from the dataset
     * @param roomId      the storage room that produced the reading, e.g. "Cold_Storage_A"
     * @param timestamp   when the reading was taken
     * @param temperature temperature in degrees Celsius
     * @param humidity    relative humidity in percent
     * @throws IllegalArgumentException if roomId is blank or a value is not a finite number
     * @throws NullPointerException     if roomId or timestamp is null
     */
    public SensorReading(int id, String roomId, LocalDateTime timestamp,
                         double temperature, double humidity) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be blank");
        }
        if (!Double.isFinite(temperature) || !Double.isFinite(humidity)) {
            throw new IllegalArgumentException("temperature and humidity must be finite numbers");
        }
        this.id = id;
        this.roomId = roomId;
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public int getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SensorReading)) {
            return false;
        }
        SensorReading other = (SensorReading) o;
        return id == other.id
                && Double.compare(temperature, other.temperature) == 0
                && Double.compare(humidity, other.humidity) == 0
                && roomId.equals(other.roomId)
                && timestamp.equals(other.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, roomId, timestamp, temperature, humidity);
    }

    @Override
    public String toString() {
        return String.format("%s @ %s: %.2f C, %.1f%% humidity",
                roomId, timestamp, temperature, humidity);
    }
}
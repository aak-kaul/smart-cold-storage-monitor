package edu.northeastern.coldstorage.sensor;

import edu.northeastern.coldstorage.model.SensorReading;
import java.util.Optional;

/** A source of temperature readings. The simulator only depends on this interface. */
public interface Sensor {
    String getId();

    /** Returns the next reading, or empty when the sensor has no more data. */
    Optional<SensorReading> nextReading();
}
package edu.northeastern.coldstorage.sensor;

import edu.northeastern.coldstorage.model.SensorReading;
import java.util.List;
import java.util.Objects;

/**
 * Factory pattern: the only place that knows which concrete class belongs to each SensorType.
 * Adding a new sensor type means one new class and one new case here.
 */
public final class SensorFactory {
    static final int DEFAULT_SPIKE_EVERY = 10;
    static final double DEFAULT_SPIKE_DELTA = 3.0;

    private SensorFactory() {
        // static factory, no instances
    }

    public static Sensor create(SensorType type, String id, List<SensorReading> readings) {
        Objects.requireNonNull(type, "type must not be null");
        return switch (type) {
            case CSV_REPLAY -> new CsvReplaySensor(id, readings);
            case SPIKE -> new SpikeSensor(new CsvReplaySensor(id, readings),
                    DEFAULT_SPIKE_EVERY, DEFAULT_SPIKE_DELTA);
        };
    }
}
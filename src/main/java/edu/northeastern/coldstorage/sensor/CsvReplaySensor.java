package edu.northeastern.coldstorage.sensor;

import edu.northeastern.coldstorage.model.SensorReading;
import java.util.List;
import java.util.Optional;

/** Replays readings loaded from the CSV dataset, one per call. */
public class CsvReplaySensor implements Sensor {
    private final String id;
    private final List<SensorReading> readings;
    private int index = 0;

    public CsvReplaySensor(String id, List<SensorReading> readings) {
        this.id = id;
        this.readings = List.copyOf(readings); // defensive copy: nobody can change our data
    }

    @Override
    public String getId() {
        return id;
    }

    // synchronized: only one thread at a time may move the index forward
    @Override
    public synchronized Optional<SensorReading> nextReading() {
        if (index >= readings.size()) {
            return Optional.empty();
        }
        return Optional.of(readings.get(index++));
    }
}
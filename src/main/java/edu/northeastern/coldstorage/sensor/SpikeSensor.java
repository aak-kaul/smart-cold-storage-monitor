package edu.northeastern.coldstorage.sensor;

import edu.northeastern.coldstorage.model.SensorReading;
import java.util.Objects;
import java.util.Optional;

/**
 * Wraps another sensor and adds a temperature spike to every N-th reading.
 * Used to trigger alerts on purpose (for demos and tests).
 */
public class SpikeSensor implements Sensor {
    private final Sensor source;
    private final int spikeEvery;
    private final double spikeDelta;
    private int count = 0;

    public SpikeSensor(Sensor source, int spikeEvery, double spikeDelta) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        if (spikeEvery <= 0) {
            throw new IllegalArgumentException("spikeEvery must be positive");
        }
        this.spikeEvery = spikeEvery;
        this.spikeDelta = spikeDelta;
    }

    @Override
    public String getId() {
        return source.getId();
    }

    @Override
    public synchronized Optional<SensorReading> nextReading() {
        Optional<SensorReading> next = source.nextReading();
        if (next.isEmpty()) {
            return next;
        }
        count++;
        if (count % spikeEvery != 0) {
            return next;
        }
        SensorReading r = next.get();
        // SensorReading is immutable, so we create a new reading with the spiked temperature
        return Optional.of(new SensorReading(r.getId(), r.getRoomId(), r.getTimestamp(),
                r.getTemperature() + spikeDelta, r.getHumidity()));
    }
}
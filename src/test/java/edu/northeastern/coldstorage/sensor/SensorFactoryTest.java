package edu.northeastern.coldstorage.sensor;

import static org.junit.jupiter.api.Assertions.*;

import edu.northeastern.coldstorage.model.SensorReading;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SensorFactoryTest {

    static List<SensorReading> readings(String room, int n, double temp) {
        List<SensorReading> list = new ArrayList<>();
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        for (int i = 0; i < n; i++) {
            list.add(new SensorReading(i, room, start.plusMinutes(i), temp, 50.0));
        }
        return list;
    }

    @Test
    void createsCorrectSensorForEachType() {
        List<SensorReading> data = readings("A", 5, 3.0);
        assertInstanceOf(CsvReplaySensor.class, SensorFactory.create(SensorType.CSV_REPLAY, "A", data));
        assertInstanceOf(SpikeSensor.class, SensorFactory.create(SensorType.SPIKE, "A", data));
    }

    @Test
    void csvReplaySensorReturnsReadingsInOrderThenEmpty() {
        List<SensorReading> data = readings("A", 3, 3.0);
        Sensor sensor = SensorFactory.create(SensorType.CSV_REPLAY, "A", data);
        assertEquals(data.get(0), sensor.nextReading().orElseThrow());
        assertEquals(data.get(1), sensor.nextReading().orElseThrow());
        assertEquals(data.get(2), sensor.nextReading().orElseThrow());
        assertTrue(sensor.nextReading().isEmpty());
    }

    @Test
    void spikeSensorAddsDeltaToEveryTenthReading() {
        Sensor sensor = SensorFactory.create(SensorType.SPIKE, "A", readings("A", 10, 3.0));
        for (int i = 1; i <= 9; i++) {
            assertEquals(3.0, sensor.nextReading().orElseThrow().getTemperature(), 1e-9);
        }
        assertEquals(6.0, sensor.nextReading().orElseThrow().getTemperature(), 1e-9);
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> SensorFactory.create(null, "A", readings("A", 1, 3.0)));
    }
}
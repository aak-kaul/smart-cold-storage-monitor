package edu.northeastern.coldstorage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageUnitTest {

    private static final double EPS = 1e-9;
    private static final String ROOM_A = "Cold_Storage_A";
    private static final String ROOM_B = "Cold_Storage_B";
    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 1, 0, 0);

    private StorageUnit unit;
    private int nextId;

    @BeforeEach
    void setUp() {
        unit = new StorageUnit(ROOM_A);
        nextId = 1;
    }

    private SensorReading reading(String room, double temperature) {
        int id = nextId++;
        return new SensorReading(id, room, START.plusMinutes(id), temperature, 50.0);
    }

    private void recordAll(double... temperatures) {
        for (double t : temperatures) {
            unit.record(reading(ROOM_A, t), ComplianceStatus.values()[0]);
        }
    }

    @Test
    void newUnitHasNoStatistics() {
        assertEquals(0, unit.getReadingCount());
        assertTrue(Double.isNaN(unit.getMean()));
        assertTrue(Double.isNaN(unit.getVariance()));
        assertTrue(Double.isNaN(unit.getStandardDeviation()));
    }

    @Test
    void runningMeanOfKnownValues() {
        recordAll(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        assertEquals(8, unit.getReadingCount());
        assertEquals(5.0, unit.getMean(), EPS);
    }

    @Test
    void runningPopulationVarianceOfKnownValues() {
        recordAll(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        assertEquals(4.0, unit.getVariance(), EPS);
    }

    @Test
    void standardDeviationOfKnownValues() {
        recordAll(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        assertEquals(2.0, unit.getStandardDeviation(), EPS);
    }

    @Test
    void standardDeviationIsSquareRootOfVariance() {
        recordAll(1.2, 3.8, 2.5, 4.1, 0.9, -0.4);
        assertEquals(Math.sqrt(unit.getVariance()), unit.getStandardDeviation(), EPS);
    }

    @Test
    void singleReadingHasZeroSpread() {
        recordAll(3.3);
        assertEquals(3.3, unit.getMean(), EPS);
        assertEquals(0.0, unit.getVariance(), EPS);
        assertEquals(0.0, unit.getStandardDeviation(), EPS);
    }

    @Test
    void identicalReadingsHaveZeroStandardDeviation() {
        recordAll(4.1, 4.1, 4.1, 4.1, 4.1);
        assertEquals(0.0, unit.getStandardDeviation(), EPS);
    }

    @Test
    void runningStatisticsMatchTwoPassCalculation() {
        double[] temps = {3.1, 2.7, 5.9, 4.4, 1.8, 6.2, 3.3, 2.9, 4.8, 5.1};
        recordAll(temps);

        double sum = 0;
        for (double t : temps) {
            sum += t;
        }
        double mean = sum / temps.length;
        double squares = 0;
        for (double t : temps) {
            squares += (t - mean) * (t - mean);
        }
        double variance = squares / temps.length;

        assertEquals(mean, unit.getMean(), EPS);
        assertEquals(variance, unit.getVariance(), EPS);
        assertEquals(Math.sqrt(variance), unit.getStandardDeviation(), EPS);
    }

    @Test
    void readingFromWrongRoomIsRejected() {
        SensorReading other = reading(ROOM_B, 4.0);
        assertThrows(IllegalArgumentException.class,
                () -> unit.record(other, ComplianceStatus.values()[0]));
    }

    @Test
    void rejectedReadingDoesNotChangeStatistics() {
        recordAll(2.0, 4.0);
        SensorReading other = reading(ROOM_B, 100.0);
        assertThrows(IllegalArgumentException.class,
                () -> unit.record(other, ComplianceStatus.values()[0]));

        assertEquals(2, unit.getReadingCount());
        assertEquals(3.0, unit.getMean(), EPS);
        assertEquals(1.0, unit.getStandardDeviation(), EPS);
        assertEquals(4.0, unit.getCurrentTemperature(), EPS);
    }
}

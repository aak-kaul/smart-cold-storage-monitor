package edu.northeastern.coldstorage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the recent-temperature history and copying added for Phase 3. */
class StorageUnitHistoryTest {

    private static final String ROOM = "Cold_Storage_A";

    @Test
    void newUnitHasEmptyHistory() {
        assertTrue(new StorageUnit(ROOM).getRecentTemperatures().isEmpty());
    }

    @Test
    void historyKeepsReadingsInOrder() {
        StorageUnit unit = new StorageUnit(ROOM);
        unit.record(reading(1, 3.0), ComplianceStatus.COMPLIANT);
        unit.record(reading(2, 3.5), ComplianceStatus.COMPLIANT);
        unit.record(reading(3, 4.5), ComplianceStatus.WARNING);

        assertEquals(List.of(3.0, 3.5, 4.5), unit.getRecentTemperatures());
    }

    @Test
    void historyKeepsOnlyTheMostRecentReadings() {
        StorageUnit unit = new StorageUnit(ROOM);
        int extra = 5;
        for (int i = 0; i < StorageUnit.HISTORY_SIZE + extra; i++) {
            unit.record(reading(i, i), ComplianceStatus.COMPLIANT);
        }
        List<Double> history = unit.getRecentTemperatures();

        assertEquals(StorageUnit.HISTORY_SIZE, history.size());
        assertEquals(extra, history.get(0), 1e-9);
        assertEquals(StorageUnit.HISTORY_SIZE + extra - 1, history.get(history.size() - 1), 1e-9);
        assertEquals(StorageUnit.HISTORY_SIZE + extra, unit.getReadingCount());
    }

    @Test
    void copyHasTheSameStateButIsIndependent() {
        StorageUnit unit = new StorageUnit(ROOM);
        unit.record(reading(1, 2.0), ComplianceStatus.COMPLIANT);
        unit.record(reading(2, 9.0), ComplianceStatus.NON_COMPLIANT);

        StorageUnit copy = unit.copy();
        assertEquals(unit.getReadingCount(), copy.getReadingCount());
        assertEquals(unit.getMean(), copy.getMean(), 1e-9);
        assertEquals(unit.getVariance(), copy.getVariance(), 1e-9);
        assertEquals(unit.getCurrentStatus(), copy.getCurrentStatus());
        assertEquals(unit.getRecentTemperatures(), copy.getRecentTemperatures());
        assertEquals(1, copy.getCount(ComplianceStatus.NON_COMPLIANT));

        copy.record(reading(3, 3.0), ComplianceStatus.COMPLIANT);
        assertEquals(2, unit.getReadingCount());
        assertNotEquals(unit.getRecentTemperatures(), copy.getRecentTemperatures());
    }

    @Test
    void historyListCannotChangeTheUnit() {
        StorageUnit unit = new StorageUnit(ROOM);
        unit.record(reading(1, 3.0), ComplianceStatus.COMPLIANT);
        unit.getRecentTemperatures().clear();
        assertEquals(1, unit.getRecentTemperatures().size());
    }

    private static SensorReading reading(int id, double temperature) {
        return new SensorReading(id, ROOM, LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(id), temperature, 85.0);
    }
}

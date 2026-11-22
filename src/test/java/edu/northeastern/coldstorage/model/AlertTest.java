package edu.northeastern.coldstorage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlertTest {

    private static final SensorReading UNSAFE =
            new SensorReading(1, "Cold_Storage_A", LocalDateTime.of(2024, 1, 1, 0, 0), 9.0, 85.0);

    @Test
    void everyAlertGetsAUniqueId() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(new Alert(UNSAFE, ComplianceStatus.NON_COMPLIANT).getId());
        }
        assertEquals(100, ids.size());
    }

    @Test
    void alertStartsUnacknowledged() {
        Alert alert = new Alert(UNSAFE, ComplianceStatus.NON_COMPLIANT);
        assertFalse(alert.isAcknowledged());
        alert.acknowledge();
        assertTrue(alert.isAcknowledged());
    }

    @Test
    void messageDescribesTheReading() {
        Alert alert = new Alert(UNSAFE, ComplianceStatus.NON_COMPLIANT);
        assertEquals("[NON-COMPLIANT] Cold_Storage_A at 2024-01-01T00:00: 9.00 C", alert.getMessage());
    }

    @Test
    void compliantReadingCannotRaiseAnAlert() {
        assertThrows(IllegalArgumentException.class, () -> new Alert(UNSAFE, ComplianceStatus.COMPLIANT));
    }
}

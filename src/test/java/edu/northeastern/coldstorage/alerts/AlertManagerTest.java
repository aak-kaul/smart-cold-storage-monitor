package edu.northeastern.coldstorage.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.SensorReading;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertManagerTest {

    private AlertManager manager;

    @BeforeEach
    void setUp() {
        manager = new AlertManager();
    }

    @Test
    void newAlertsBecomeActive() {
        Alert first = alert(ComplianceStatus.WARNING);
        Alert second = alert(ComplianceStatus.NON_COMPLIANT);
        manager.onAlert(first);
        manager.onAlert(second);

        assertEquals(List.of(first, second), manager.getActiveAlerts());
        assertEquals(2, manager.getActiveCount());
        assertEquals(2, manager.getTotalAlerts());
    }

    @Test
    void acknowledgingRemovesOnlyThatAlert() {
        Alert first = alert(ComplianceStatus.WARNING);
        Alert second = alert(ComplianceStatus.WARNING);
        manager.onAlert(first);
        manager.onAlert(second);

        assertTrue(manager.acknowledge(first.getId()));

        assertTrue(first.isAcknowledged());
        assertFalse(second.isAcknowledged());
        assertEquals(List.of(second), manager.getActiveAlerts());
    }

    @Test
    void acknowledgingUnknownAlertReturnsFalse() {
        assertFalse(manager.acknowledge(999_999));
    }

    @Test
    void acknowledgeAllClearsActiveAlertsButKeepsHistory() {
        manager.onAlert(alert(ComplianceStatus.WARNING));
        manager.onAlert(alert(ComplianceStatus.NON_COMPLIANT));

        assertEquals(2, manager.acknowledgeAll());

        assertEquals(0, manager.getActiveCount());
        assertEquals(2, manager.getRecentAlerts(10).size());
        assertEquals(2, manager.getTotalAlerts());
    }

    @Test
    void alarmSoundsOnlyForNonCompliantAlerts() {
        manager.onAlert(alert(ComplianceStatus.WARNING));
        assertFalse(manager.isAlarmActive());

        manager.onAlert(alert(ComplianceStatus.NON_COMPLIANT));
        assertTrue(manager.isAlarmActive());
    }

    @Test
    void silencedAlarmStaysQuietUntilNextCriticalAlert() {
        manager.onAlert(alert(ComplianceStatus.NON_COMPLIANT));
        manager.silenceAlarm();
        assertFalse(manager.isAlarmActive());

        manager.onAlert(alert(ComplianceStatus.WARNING));
        assertFalse(manager.isAlarmActive(), "a warning must not re-arm the alarm");

        manager.onAlert(alert(ComplianceStatus.NON_COMPLIANT));
        assertTrue(manager.isAlarmActive(), "a new critical alert re-arms the alarm");
    }

    @Test
    void alarmStopsWhenCriticalAlertsAreAcknowledged() {
        Alert critical = alert(ComplianceStatus.NON_COMPLIANT);
        manager.onAlert(critical);
        manager.acknowledge(critical.getId());

        assertFalse(manager.isAlarmActive());
    }

    @Test
    void recentAlertsAreNewestFirstAndLimited() {
        Alert older = alert(ComplianceStatus.WARNING);
        Alert newer = alert(ComplianceStatus.WARNING);
        manager.onAlert(older);
        manager.onAlert(newer);

        assertEquals(List.of(newer), manager.getRecentAlerts(1));
        assertEquals(List.of(newer, older), manager.getRecentAlerts(5));
    }

    @Test
    void historyIsCappedAtRecentLimit() {
        for (int i = 0; i < AlertManager.RECENT_LIMIT + 20; i++) {
            manager.onAlert(alert(ComplianceStatus.WARNING));
        }
        assertEquals(AlertManager.RECENT_LIMIT, manager.getRecentAlerts(Integer.MAX_VALUE).size());
    }

    @Test
    void findActiveLocatesAlertById() {
        Alert a = alert(ComplianceStatus.WARNING);
        manager.onAlert(a);
        assertTrue(manager.findActive(a.getId()).isPresent());
        manager.acknowledge(a.getId());
        assertFalse(manager.findActive(a.getId()).isPresent());
    }

    private static Alert alert(ComplianceStatus status) {
        SensorReading reading = new SensorReading(1, "Cold_Storage_A",
                LocalDateTime.of(2024, 1, 1, 0, 0), status == ComplianceStatus.WARNING ? 4.5 : 9.0, 85.0);
        return new Alert(reading, status);
    }
}

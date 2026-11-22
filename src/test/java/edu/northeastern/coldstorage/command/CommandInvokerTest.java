package edu.northeastern.coldstorage.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.logging.AuditEntry;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.SensorReading;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandInvokerTest {

    private AlertManager alertManager;
    private CommandInvoker invoker;
    private AuditLogger audit;

    @BeforeEach
    void setUp() {
        alertManager = new AlertManager();
        audit = AuditLogger.getInstance();
        invoker = new CommandInvoker(audit);
    }

    @Test
    void acknowledgeCommandAcknowledgesTheAlert() {
        Alert alert = critical();
        alertManager.onAlert(alert);

        String result = invoker.execute("tester", new AcknowledgeAlertCommand(alertManager, alert.getId()));

        assertEquals("Acknowledged alert #" + alert.getId(), result);
        assertTrue(alert.isAcknowledged());
    }

    @Test
    void acknowledgeCommandReportsUnknownAlert() {
        String result = invoker.execute("tester", new AcknowledgeAlertCommand(alertManager, 123_456_789));
        assertEquals("Alert #123456789 is not active", result);
    }

    @Test
    void acknowledgeAllCommandClearsActiveAlerts() {
        alertManager.onAlert(critical());
        alertManager.onAlert(critical());

        String result = invoker.execute("tester", new AcknowledgeAllAlertsCommand(alertManager));

        assertEquals("Acknowledged 2 alerts", result);
        assertEquals(0, alertManager.getActiveCount());
    }

    @Test
    void silenceCommandSilencesTheAlarm() {
        alertManager.onAlert(critical());
        assertTrue(alertManager.isAlarmActive());

        invoker.execute("tester", new SilenceAlarmCommand(alertManager));

        assertFalse(alertManager.isAlarmActive());
    }

    @Test
    void everyCommandIsWrittenToTheAuditLog() {
        int before = audit.getEntries().size();

        invoker.execute("web", new SilenceAlarmCommand(alertManager));

        List<AuditEntry> entries = audit.getEntries();
        assertEquals(before + 1, entries.size());
        AuditEntry last = entries.get(entries.size() - 1);
        assertEquals(AuditLogger.OPERATOR_ACTION, last.getEventType());
        assertEquals("Silence alarm by web: Alarm silenced", last.getDetails());
        assertTrue(audit.verifyIntegrity());
    }

    @Test
    void invokerKeepsAHistoryOfActions() {
        invoker.execute("desktop", new SilenceAlarmCommand(alertManager));
        invoker.execute("desktop", new AcknowledgeAllAlertsCommand(alertManager));

        assertEquals(List.of("Silence alarm by desktop: Alarm silenced",
                "Acknowledge all alerts by desktop: Acknowledged 0 alerts"), invoker.getHistory());
    }

    @Test
    void nullCommandIsRejected() {
        assertThrows(NullPointerException.class, () -> invoker.execute("tester", null));
    }

    private static Alert critical() {
        return new Alert(new SensorReading(1, "Cold_Storage_A", LocalDateTime.of(2024, 1, 1, 0, 0), 9.0, 85.0),
                ComplianceStatus.NON_COMPLIANT);
    }
}

package edu.northeastern.coldstorage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.app.AppOptions;
import edu.northeastern.coldstorage.app.MonitoringSystem;
import edu.northeastern.coldstorage.logging.AuditEntry;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the primary use case "Detect unsafe storage conditions",
 * run on the real IoT.csv dataset with every real component wired together:
 * reader -> controller -> compliance checker -> storage units -> forecasts ->
 * console view + alert manager + audit log.
 */
class BatchWorkflowIntegrationTest {

    @Test
    void wholeDatasetIsProcessedEndToEnd() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MonitoringSystem system = new MonitoringSystem(AppOptions.parse(new String[] {"--alerts=5"}),
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        AuditLogger audit = AuditLogger.getInstance();
        int auditBefore = audit.getEntries().size();

        List<SensorReading> readings = system.loadReadings();
        MonitoringReport report = system.runBatch(readings);
        system.printSummary(report, readings);

        // 1. Every reading was received and classified (known results for IoT.csv).
        assertEquals(4977, report.getTotalReadings());
        assertEquals(2404, report.getCount(ComplianceStatus.COMPLIANT));
        assertEquals(2207, report.getCount(ComplianceStatus.WARNING));
        assertEquals(366, report.getCount(ComplianceStatus.NON_COMPLIANT));
        assertFalse(report.isContractorCompliant(), "7.4% unsafe is above the 5% limit");

        // 2. Readings were stored per room, and each room has a forecast.
        assertEquals(3, report.getUnits().size());
        assertEquals(3, report.getForecasts().size());

        // 3. Every unsafe reading reached every observer.
        int unsafe = 2207 + 366;
        assertEquals(unsafe, system.getAlertManager().getTotalAlerts());
        assertEquals(unsafe, system.getConsoleView().getAlertsReceived());
        List<AuditEntry> newEntries = audit.getEntries().subList(auditBefore, audit.getEntries().size());
        long violations = newEntries.stream()
                .filter(e -> e.getEventType().equals(AuditLogger.TEMPERATURE_VIOLATION)).count();
        assertEquals(unsafe, violations);
        assertTrue(audit.verifyIntegrity());

        // 4. The user sees the summary, forecasts, model accuracy and integrity result.
        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Loaded 4977 readings from IoT.csv"));
        assertTrue(printed.contains("MONITORING SUMMARY"));
        assertTrue(printed.contains("FORECAST (Moving average, 3 readings ahead)"));
        assertTrue(printed.contains("Next-reading forecast by model"));
        assertTrue(printed.contains("Prediction accuracy on this data"));
        assertTrue(printed.contains("Contractor (TMC) compliance: FAIL"));
        assertTrue(printed.contains("integrity check: PASSED"));
    }

    @Test
    void processingMeetsTheThroughputTarget() throws Exception {
        // Non-functional requirement: process at least 100 readings per second.
        MonitoringSystem system = new MonitoringSystem(AppOptions.parse(new String[] {"--alerts=0"}),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        List<SensorReading> readings = system.loadReadings();

        long start = System.nanoTime();
        system.runBatch(readings);
        double seconds = (System.nanoTime() - start) / 1e9;

        double perSecond = readings.size() / seconds;
        assertTrue(perSecond >= 100, "processed only " + perSecond + " readings per second");
    }

    @Test
    void highFrequencyDatasetAlsoWorksEndToEnd() throws Exception {
        MonitoringSystem system = new MonitoringSystem(
                AppOptions.parse(new String[] {"--data=cold_storage_high_frequency.csv", "--alerts=0"}),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        MonitoringReport report = system.runBatch(system.loadReadings());

        assertEquals(10075, report.getTotalReadings());
        assertEquals(1, report.getUnits().size());
        assertEquals("Cold_Storage_RealTime", report.getUnits().get(0).getRoomId());
    }
}

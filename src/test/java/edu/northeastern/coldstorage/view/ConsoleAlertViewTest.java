package edu.northeastern.coldstorage.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsoleAlertViewTest {

    private ByteArrayOutputStream buffer;
    private PrintStream out;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    @Test
    void nonCompliantAlertIsPrintedInRed() {
        ConsoleAlertView view = new ConsoleAlertView(out, 10);
        view.onAlert(new Alert(reading("Cold_Storage_A", 1, 9.0), ComplianceStatus.NON_COMPLIANT));

        String printed = output();
        assertTrue(printed.contains("ALERT [NON-COMPLIANT] Cold_Storage_A"));
        assertTrue(printed.contains("9.00 C"));
        assertTrue(printed.contains("-> RED"));
    }

    @Test
    void warningAlertIsPrintedInYellow() {
        ConsoleAlertView view = new ConsoleAlertView(out, 10);
        view.onAlert(new Alert(reading("Cold_Storage_B", 1, 4.5), ComplianceStatus.WARNING));

        assertTrue(output().contains("ALERT [WARNING] Cold_Storage_B"));
        assertTrue(output().contains("-> YELLOW"));
    }

    @Test
    void printsOnlyUpToTheLimitButCountsEveryAlert() {
        ConsoleAlertView view = new ConsoleAlertView(out, 2);
        for (int i = 1; i <= 5; i++) {
            view.onAlert(new Alert(reading("Cold_Storage_A", i, 9.0), ComplianceStatus.NON_COMPLIANT));
        }

        assertEquals(2, countOccurrences(output()));
        assertEquals(5, view.getAlertsReceived());
    }

    @Test
    void reportMentionsAlertsThatWereNotPrinted() {
        ConsoleAlertView view = new ConsoleAlertView(out, 2);
        MonitorController controller = new MonitorController(new ComplianceChecker());
        controller.addListener(view);

        MonitoringReport report = controller.processAll(List.of(
                reading("Cold_Storage_A", 1, 9.0), reading("Cold_Storage_A", 2, 9.0),
                reading("Cold_Storage_A", 3, 9.0), reading("Cold_Storage_A", 4, 9.0),
                reading("Cold_Storage_A", 5, 9.0)));
        view.showReport(report);

        assertTrue(output().contains("... and 3 more alerts"));
    }

    @Test
    void noExtraMessageWhenEveryAlertWasPrinted() {
        ConsoleAlertView view = new ConsoleAlertView(out, 10);
        MonitorController controller = new MonitorController(new ComplianceChecker());
        controller.addListener(view);

        view.showReport(controller.processAll(List.of(reading("Cold_Storage_A", 1, 9.0))));

        assertFalse(output().contains("more alerts"));
    }

    @Test
    void zeroLimitPrintsNoAlerts() {
        ConsoleAlertView view = new ConsoleAlertView(out, 0);
        view.onAlert(new Alert(reading("Cold_Storage_A", 1, 9.0), ComplianceStatus.NON_COMPLIANT));

        assertFalse(output().contains("ALERT "));
        assertEquals(1, view.getAlertsReceived());
    }

    @Test
    void summaryShowsTotalsRoomsAndContractorResult() {
        ConsoleAlertView view = new ConsoleAlertView(out, 0);
        MonitorController controller = new MonitorController(new ComplianceChecker());
        controller.addListener(view);

        MonitoringReport report = controller.processAll(List.of(
                reading("Cold_Storage_A", 1, 3.0), reading("Cold_Storage_A", 2, 3.0),
                reading("Cold_Storage_B", 3, 4.5), reading("Cold_Storage_B", 4, 9.5)));
        view.showReport(report);

        String printed = output();
        assertTrue(printed.contains("MONITORING SUMMARY"));
        assertTrue(printed.contains("Readings processed : 4"));
        assertTrue(printed.contains("Alerts raised      : 2"));
        assertTrue(printed.contains("STATUS BREAKDOWN"));
        assertTrue(printed.contains("Cold_Storage_A"));
        assertTrue(printed.contains("Cold_Storage_B"));
        assertTrue(printed.contains("Contractor (TMC) compliance: FAIL"));
    }

    @Test
    void barReflectsThePercentage() {
        assertEquals("#".repeat(30), ConsoleAlertView.bar(100));
        assertEquals(".".repeat(30), ConsoleAlertView.bar(0));
        assertEquals("#".repeat(15) + ".".repeat(15), ConsoleAlertView.bar(50));
    }

    @Test
    void negativeLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConsoleAlertView(out, -1));
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text) {
        int count = 0;
        int index = text.indexOf("ALERT ");
        while (index != -1) {
            count++;
            index = text.indexOf("ALERT ", index + "ALERT ".length());
        }
        return count;
    }

    private static SensorReading reading(String room, int id, double temperature) {
        return new SensorReading(id, room, LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(id),
                temperature, 85.0);
    }
}

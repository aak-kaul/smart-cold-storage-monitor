package edu.northeastern.coldstorage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.controller.AlertListener;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorControllerTest {

    private MonitorController controller;
    private List<Alert> received;

    @BeforeEach
    void setUp() {
        controller = new MonitorController(new ComplianceChecker());
        received = new ArrayList<>();
        controller.addListener(received::add);
    }

    @Test
    void compliantReadingDoesNotAlert() {
        assertEquals(ComplianceStatus.COMPLIANT, controller.process(reading("A", 1, 3.0)));
        assertTrue(received.isEmpty());
    }

    @Test
    void unsafeReadingsNotifyListeners() {
        controller.process(reading("A", 1, 4.5));
        controller.process(reading("A", 2, 12.0));

        assertEquals(2, received.size());
        assertEquals(ComplianceStatus.WARNING, received.get(0).getStatus());
        assertEquals(ComplianceStatus.NON_COMPLIANT, received.get(1).getStatus());
        assertFalse(received.get(1).isAcknowledged());
    }

    @Test
    void everyListenerIsNotified() {
        List<Alert> second = new ArrayList<>();
        controller.addListener(second::add);
        controller.process(reading("A", 1, 20.0));
        assertEquals(1, received.size());
        assertEquals(1, second.size());
    }

    @Test
    void removedListenerIsNotNotified() {
        List<Alert> other = new ArrayList<>();
        AlertListener listener = other::add;
        controller.addListener(listener);
        controller.removeListener(listener);
        controller.process(reading("A", 1, 20.0));
        assertTrue(other.isEmpty());
    }

    @Test
    void readingsAreStoredPerRoom() {
        controller.process(reading("A", 1, 2.0));
        controller.process(reading("A", 2, 4.0));
        controller.process(reading("B", 3, 10.0));

        StorageUnit a = controller.getUnit("A");
        assertEquals(2, a.getReadingCount());
        assertEquals(4.0, a.getCurrentTemperature(), 1e-9);
        assertEquals(3.0, a.getMean(), 1e-9);
        assertEquals(1.0, a.getVariance(), 1e-9);
        assertEquals(ComplianceStatus.NON_COMPLIANT, controller.getUnit("B").getCurrentStatus());
    }

    @Test
    void reportSummarisesTheRun() {
        MonitoringReport report = controller.processAll(List.of(
                reading("A", 1, 3.0), reading("A", 2, 3.0), reading("B", 3, 4.5), reading("B", 4, 9.5)));

        assertEquals(4, report.getTotalReadings());
        assertEquals(2, report.getCount(ComplianceStatus.COMPLIANT));
        assertEquals(1, report.getCount(ComplianceStatus.WARNING));
        assertEquals(1, report.getCount(ComplianceStatus.NON_COMPLIANT));
        assertEquals(5.0, report.getMeanTemperature(), 1e-9);
        assertEquals(25.0, report.getPercentNonCompliant(), 1e-9);
        assertFalse(report.isContractorCompliant());
        assertEquals(2, report.getUnits().size());
    }

    @Test
    void reportBeforeAnyReadingIsRejected() {
        assertThrows(IllegalStateException.class, () -> controller.buildReport());
    }

    private static SensorReading reading(String room, int id, double temperature) {
        return new SensorReading(id, room, LocalDateTime.of(2024, 1, 1, id % 24, 0), temperature, 85.0);
    }
}
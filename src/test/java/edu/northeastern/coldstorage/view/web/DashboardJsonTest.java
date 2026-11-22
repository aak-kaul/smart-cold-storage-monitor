package edu.northeastern.coldstorage.view.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.SensorReading;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardJsonTest {

    private MonitorController controller;
    private AlertManager alerts;

    @BeforeEach
    void setUp() {
        controller = new MonitorController(new ComplianceChecker());
        alerts = new AlertManager();
        controller.addListener(alerts);
    }

    @Test
    void statusBeforeAnyReadingHasNoRooms() {
        String json = DashboardJson.status(controller, alerts);
        assertTrue(json.contains("\"readings\":0"));
        assertTrue(json.contains("\"rooms\":[]"));
        assertTrue(json.contains("\"limits\":{\"minSafe\":2.000,\"maxSafe\":4.000,\"warningMargin\":1.000}"));
        assertTrue(json.contains("\"predictor\":\"Moving average\""));
    }

    @Test
    void statusDescribesRoomsCountsAndForecasts() {
        feed("Cold_Storage_A", 3.0, 3.0, 9.0);
        String json = DashboardJson.status(controller, alerts);

        assertTrue(json.contains("\"readings\":3"));
        assertTrue(json.contains("\"counts\":{\"COMPLIANT\":2,\"WARNING\":0,\"NON_COMPLIANT\":1}"));
        assertTrue(json.contains("\"roomId\":\"Cold_Storage_A\""));
        assertTrue(json.contains("\"status\":\"NON_COMPLIANT\""));
        assertTrue(json.contains("\"history\":[3.000,3.000,9.000]"));
        assertTrue(json.contains("\"forecast\":{\"temperature\":5.000"));
        assertTrue(json.contains("\"contractorCompliant\":false"));
        assertTrue(json.contains("\"alarmActive\":true"));
    }

    @Test
    void alertsListActiveAndRecent() {
        feed("Cold_Storage_B", 4.5);
        String json = DashboardJson.alerts(alerts);

        assertTrue(json.startsWith("{\"active\":[{\"id\":"));
        assertTrue(json.contains("\"roomId\":\"Cold_Storage_B\""));
        assertTrue(json.contains("\"statusLabel\":\"WARNING\""));
        assertTrue(json.contains("\"recent\":[{"));
    }

    @Test
    void messageIsASimpleObject() {
        assertEquals("{\"message\":\"Alarm silenced\"}", DashboardJson.message("Alarm silenced"));
    }

    private void feed(String room, double... temps) {
        for (int i = 0; i < temps.length; i++) {
            controller.process(new SensorReading(i, room, LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i),
                    temps[i], 80.0));
        }
    }
}

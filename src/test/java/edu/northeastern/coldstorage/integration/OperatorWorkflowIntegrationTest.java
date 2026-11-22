package edu.northeastern.coldstorage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.app.AppOptions;
import edu.northeastern.coldstorage.app.MonitoringSystem;
import edu.northeastern.coldstorage.logging.AuditEntry;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.sensor.SensorFactory;
import edu.northeastern.coldstorage.sensor.SensorSimulator;
import edu.northeastern.coldstorage.sensor.SensorType;
import edu.northeastern.coldstorage.view.web.DashboardServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the operator workflow through the web dashboard:
 * a failing room raises alerts -> the alarm sounds -> the operator silences it and
 * acknowledges the alerts over HTTP -> every action is in the tamper-evident audit log.
 */
class OperatorWorkflowIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private MonitoringSystem system;
    private DashboardServer server;

    @BeforeEach
    void setUp() throws Exception {
        system = new MonitoringSystem(AppOptions.parse(new String[] {"--alerts=0"}),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        server = new DashboardServer(system.getController(), system.getAlertManager(), system.getInvoker());
        server.start(0); // any free port
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void operatorHandlesAFailingRoomFromTheWebDashboard() throws Exception {
        // A room whose compressor is failing: it warms by 0.2 C per reading, from 3.0 C to 10.8 C.
        List<SensorReading> warming = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            warming.add(reading("Cold_Storage_X", i, 3.0 + 0.2 * i));
        }
        SensorSimulator simulator = new SensorSimulator(
                List.of(SensorFactory.create(SensorType.CSV_REPLAY, "Cold_Storage_X", warming)),
                system.getController(), 1);
        simulator.start();
        assertTrue(simulator.awaitCompletion(30, TimeUnit.SECONDS));
        simulator.stop();

        // The room ends above 5 C, so critical alerts exist and the alarm is sounding.
        assertTrue(system.getAlertManager().isAlarmActive());
        assertTrue(get("/api/status").contains("\"alarmActive\":true"));
        String status = get("/api/status");
        assertTrue(status.contains("\"roomId\":\"Cold_Storage_X\""));
        assertTrue(status.contains("\"risk\":true"), "the forecast should flag the warming room");

        AuditLogger audit = AuditLogger.getInstance();
        int auditBefore = audit.getEntries().size();

        // Operator silences the alarm, acknowledges one alert, then all the rest.
        assertEquals("{\"message\":\"Alarm silenced\"}", post("/api/alarm/silence"));
        Alert first = system.getAlertManager().getActiveAlerts().get(0);
        post("/api/alerts/" + first.getId() + "/acknowledge");
        int remaining = system.getAlertManager().getActiveCount();
        assertEquals("{\"message\":\"Acknowledged " + remaining + " alerts\"}", post("/api/alerts/acknowledge-all"));

        assertFalse(system.getAlertManager().isAlarmActive());
        assertEquals(0, system.getAlertManager().getActiveCount());
        assertTrue(first.isAcknowledged());

        // Each of the three actions was audited, and the hash chain is intact.
        List<AuditEntry> actions = audit.getEntries().subList(auditBefore, audit.getEntries().size());
        assertEquals(3, actions.size());
        assertTrue(actions.stream().allMatch(e -> e.getEventType().equals(AuditLogger.OPERATOR_ACTION)));
        assertTrue(actions.get(0).getDetails().startsWith("Silence alarm by web"));
        assertTrue(audit.verifyIntegrity());
    }

    @Test
    void newCriticalReadingReArmsASilencedAlarm() throws Exception {
        feed(9.0);
        post("/api/alarm/silence");
        assertFalse(system.getAlertManager().isAlarmActive());

        feed(10.0);
        assertTrue(system.getAlertManager().isAlarmActive());
    }

    @Test
    void switchingTheModelFromTheDashboardChangesForecasts() throws Exception {
        feed(2.5, 3.0, 3.5, 4.0);
        assertTrue(get("/api/status").contains("\"model\":\"Moving average\""));

        post("/api/predictor?model=linear");

        String status = get("/api/status");
        assertTrue(status.contains("\"model\":\"Linear regression\""));
        assertTrue(status.contains("\"temperature\":5.500"));
    }

    private void feed(double... temps) {
        for (int i = 0; i < temps.length; i++) {
            system.getController().process(reading("Cold_Storage_A", i, temps[i]));
        }
    }

    private static SensorReading reading(String room, int id, double temperature) {
        return new SensorReading(id, room, LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(id), temperature, 80.0);
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody()).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.getPort() + path);
    }
}

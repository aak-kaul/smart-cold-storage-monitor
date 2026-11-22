package edu.northeastern.coldstorage.view.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.command.CommandInvoker;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.SensorReading;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Starts the real web server on a free port and talks to it over HTTP. */
class DashboardServerTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private MonitorController controller;
    private AlertManager alerts;
    private DashboardServer server;

    @BeforeEach
    void setUp() throws IOException {
        controller = new MonitorController(new ComplianceChecker());
        alerts = new AlertManager();
        controller.addListener(alerts);
        server = new DashboardServer(controller, alerts, new CommandInvoker(AuditLogger.getInstance()));
        server.start(0); // 0 = any free port
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void servesTheDashboardPage() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("Smart Cold Storage Monitor"));
    }

    @Test
    void statusEndpointReturnsLiveData() throws Exception {
        feed(3.0, 9.0);
        HttpResponse<String> response = get("/api/status");
        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("\"readings\":2"));
        assertTrue(response.body().contains("\"roomId\":\"Cold_Storage_A\""));
    }

    @Test
    void acknowledgeEndpointAcknowledgesTheAlert() throws Exception {
        feed(9.0);
        Alert alert = alerts.getActiveAlerts().get(0);

        HttpResponse<String> response = post("/api/alerts/" + alert.getId() + "/acknowledge");

        assertEquals(200, response.statusCode());
        assertEquals("{\"message\":\"Acknowledged alert #" + alert.getId() + "\"}", response.body());
        assertTrue(alert.isAcknowledged());
        assertTrue(get("/api/alerts").body().startsWith("{\"active\":[]"));
    }

    @Test
    void acknowledgeAllAndSilenceEndpointsWork() throws Exception {
        feed(9.0, 9.5);
        assertTrue(alerts.isAlarmActive());

        assertEquals(200, post("/api/alarm/silence").statusCode());
        assertFalse(alerts.isAlarmActive());

        assertEquals("{\"message\":\"Acknowledged 2 alerts\"}", post("/api/alerts/acknowledge-all").body());
        assertEquals(0, alerts.getActiveCount());
    }

    @Test
    void predictorEndpointSwitchesTheModel() throws Exception {
        HttpResponse<String> response = post("/api/predictor?model=linear");
        assertEquals(200, response.statusCode());
        assertEquals("Linear regression", controller.getPredictor().name());
    }

    @Test
    void badRequestsGetHelpfulErrors() throws Exception {
        assertEquals(404, get("/nope").statusCode());
        assertEquals(404, post("/api/nope").statusCode());
        assertEquals(400, post("/api/predictor?model=magic").statusCode());
        assertEquals(400, post("/api/predictor").statusCode());
        HttpRequest delete = HttpRequest.newBuilder(uri("/api/status")).DELETE().build();
        assertEquals(405, http.send(delete, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void webActionsAreAudited() throws Exception {
        int before = AuditLogger.getInstance().getEntries().size();
        post("/api/alarm/silence");
        assertEquals(before + 1, AuditLogger.getInstance().getEntries().size());
    }

    @Test
    void serverCannotBeStartedTwice() {
        assertThrows(IllegalStateException.class, () -> server.start(0));
    }

    private void feed(double... temps) {
        for (int i = 0; i < temps.length; i++) {
            controller.process(new SensorReading(i, "Cold_Storage_A",
                    LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i), temps[i], 80.0));
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.getPort() + path);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody()).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

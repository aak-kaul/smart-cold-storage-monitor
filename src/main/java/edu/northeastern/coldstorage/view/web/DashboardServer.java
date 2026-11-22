package edu.northeastern.coldstorage.view.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.command.AcknowledgeAlertCommand;
import edu.northeastern.coldstorage.command.AcknowledgeAllAlertsCommand;
import edu.northeastern.coldstorage.command.ChangePredictorCommand;
import edu.northeastern.coldstorage.command.CommandInvoker;
import edu.northeastern.coldstorage.command.SilenceAlarmCommand;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.PredictionModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The web dashboard (Functional Requirement 4): a small REST API plus one HTML page,
 * served by the HTTP server built into the JDK, so no web framework is needed.
 *
 * <pre>
 * GET  /                               the dashboard page
 * GET  /api/status                     totals, limits, rooms, forecasts (JSON)
 * GET  /api/alerts                     active and recent alerts (JSON)
 * POST /api/alerts/{id}/acknowledge    acknowledge one alert
 * POST /api/alerts/acknowledge-all     acknowledge every active alert
 * POST /api/alarm/silence              silence the alarm
 * POST /api/predictor?model=linear     switch prediction model (linear | moving-average)
 * </pre>
 *
 * Every POST goes through the {@link CommandInvoker}, so web actions are audited exactly
 * like desktop actions.
 */
public class DashboardServer {

    /** Default port: open http://localhost:8080 in a browser. */
    public static final int DEFAULT_PORT = 8080;
    private static final String OPERATOR = "web";
    private static final String PAGE = "/web/dashboard.html";

    private final MonitorController controller;
    private final AlertManager alertManager;
    private final CommandInvoker invoker;
    private HttpServer server;
    private ExecutorService executor;

    public DashboardServer(MonitorController controller, AlertManager alertManager, CommandInvoker invoker) {
        this.controller = Objects.requireNonNull(controller, "controller must not be null");
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager must not be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker must not be null");
    }

    /**
     * Starts listening.
     *
     * @param port the port to use, or 0 to let the system pick a free one (used by tests)
     * @throws IOException if the port is already in use
     */
    public synchronized void start(int port) throws IOException {
        if (server != null) {
            throw new IllegalStateException("Server already started");
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "web-dashboard");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
    }

    /** Stops the server. */
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
            server = null;
        }
    }

    /** The port the server is listening on. */
    public synchronized int getPort() {
        if (server == null) {
            throw new IllegalStateException("Server not started");
        }
        return server.getAddress().getPort();
    }

    /** Routes one request to the right handler. Any error becomes a JSON error response. */
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (method.equals("GET")) {
                handleGet(exchange, path);
            } else if (method.equals("POST")) {
                handlePost(exchange, path);
            } else {
                send(exchange, 405, "application/json", DashboardJson.message("Method not allowed"));
            }
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "application/json", DashboardJson.message(e.getMessage()));
        } catch (RuntimeException e) {
            send(exchange, 500, "application/json", DashboardJson.message("Server error: " + e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        switch (path) {
            case "/":
            case "/index.html":
                send(exchange, 200, "text/html; charset=utf-8", loadPage());
                break;
            case "/api/status":
                send(exchange, 200, "application/json", DashboardJson.status(controller, alertManager));
                break;
            case "/api/alerts":
                send(exchange, 200, "application/json", DashboardJson.alerts(alertManager));
                break;
            default:
                send(exchange, 404, "application/json", DashboardJson.message("Not found: " + path));
        }
    }

    private void handlePost(HttpExchange exchange, String path) throws IOException {
        String result;
        if (path.equals("/api/alerts/acknowledge-all")) {
            result = invoker.execute(OPERATOR, new AcknowledgeAllAlertsCommand(alertManager));
        } else if (path.equals("/api/alarm/silence")) {
            result = invoker.execute(OPERATOR, new SilenceAlarmCommand(alertManager));
        } else if (path.equals("/api/predictor")) {
            PredictionModel model = PredictionModel.fromName(queryParam(exchange, "model"));
            result = invoker.execute(OPERATOR, new ChangePredictorCommand(controller, model));
        } else if (path.matches("/api/alerts/\\d+/acknowledge")) {
            long id = Long.parseLong(path.split("/")[3]);
            result = invoker.execute(OPERATOR, new AcknowledgeAlertCommand(alertManager, id));
        } else {
            send(exchange, 404, "application/json", DashboardJson.message("Not found: " + path));
            return;
        }
        send(exchange, 200, "application/json", DashboardJson.message(result));
    }

    /** Reads one query parameter, e.g. "model" from "?model=linear". */
    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && parts[0].equals(name)) {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("Missing query parameter: " + name);
    }

    private static String loadPage() throws IOException {
        try (InputStream in = DashboardServer.class.getResourceAsStream(PAGE)) {
            if (in == null) {
                throw new IOException("Dashboard page not found on classpath: " + PAGE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

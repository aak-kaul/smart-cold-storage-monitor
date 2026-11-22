package edu.northeastern.coldstorage.app;

import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.command.CommandInvoker;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.data.ColdStorageDataReader;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.ml.PredictionModel;
import edu.northeastern.coldstorage.ml.PredictionService;
import edu.northeastern.coldstorage.ml.PredictorEvaluator;
import edu.northeastern.coldstorage.ml.TemperatureForecaster;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import edu.northeastern.coldstorage.sensor.Sensor;
import edu.northeastern.coldstorage.sensor.SensorFactory;
import edu.northeastern.coldstorage.sensor.SensorSimulator;
import edu.northeastern.coldstorage.view.ConsoleAlertView;
import edu.northeastern.coldstorage.view.desktop.DesktopDashboard;
import edu.northeastern.coldstorage.view.web.DashboardServer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the whole system and connects the parts ("composition root").
 *
 * <p>Keeping all the wiring in one place means {@code Main} stays short and the integration
 * tests can run exactly the same system the user runs.
 *
 * <pre>
 *  sensors --&gt; MonitorController --&gt; observers: ConsoleAlertView, AlertManager, AuditLogger
 *                    |                                         ^
 *                    +-- forecasts --&gt; views -- commands -----+ (via CommandInvoker)
 * </pre>
 */
public class MonitoringSystem {

    private final AppOptions options;
    private final PrintStream out;
    private final AuditLogger audit;
    private final MonitorController controller;
    private final AlertManager alertManager;
    private final ConsoleAlertView consoleView;
    private final CommandInvoker invoker;

    /**
     * Creates the system and registers every observer with the controller.
     *
     * @param options command-line options
     * @param out     where console output goes (System.out, or a buffer in tests)
     */
    public MonitoringSystem(AppOptions options, PrintStream out) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.audit = AuditLogger.getInstance();

        ComplianceChecker checker = new ComplianceChecker();
        TemperatureForecaster forecaster = new TemperatureForecaster(checker,
                options.getPredictionModel().create(), TemperatureForecaster.DEFAULT_STEPS_AHEAD);
        this.controller = new MonitorController(checker, forecaster);
        this.alertManager = new AlertManager();
        this.consoleView = new ConsoleAlertView(out, options.getAlertsToPrint());
        this.invoker = new CommandInvoker(audit);

        // Observer pattern: each listener reacts to alerts in its own way.
        controller.addListener(consoleView);
        controller.addListener(alertManager);
        controller.addListener(audit);
    }

    /**
     * Loads the dataset named in the options: a file on disk if it exists, otherwise a
     * dataset bundled in src/main/resources.
     *
     * @throws IOException if the data cannot be found or read
     */
    public List<SensorReading> loadReadings() throws IOException {
        ColdStorageDataReader reader = new ColdStorageDataReader();
        String source = options.getData();
        Path file = Path.of(source);
        List<SensorReading> readings = Files.isRegularFile(file)
                ? reader.readFromFile(file)
                : reader.readFromResource(source);
        String message = String.format("Loaded %d readings from %s (%d invalid rows skipped)",
                readings.size(), source, reader.getSkippedRowCount());
        audit.log(AuditLogger.SYSTEM, message);
        out.println(message);
        ComplianceChecker rules = controller.getChecker();
        out.printf("Safe range: %.1f to %.1f C, warning margin +/- %.1f C, prediction model: %s%n%n",
                rules.getMinSafe(), rules.getMaxSafe(), rules.getWarningMargin(), controller.getPredictor().name());
        return readings;
    }

    /** Processes every reading at once (batch mode) and returns the report. */
    public MonitoringReport runBatch(List<SensorReading> readings) {
        audit.log(AuditLogger.SYSTEM, "Batch monitoring started");
        return controller.processAll(readings);
    }

    /**
     * Creates one live sensor per room (Factory pattern) and starts them
     * (simulate, web and gui modes).
     */
    public SensorSimulator startSimulation(List<SensorReading> readings) {
        List<Sensor> sensors = new ArrayList<>();
        for (Map.Entry<String, List<SensorReading>> room : groupByRoom(readings).entrySet()) {
            sensors.add(SensorFactory.create(options.getSensorType(), room.getKey(), room.getValue()));
        }
        SensorSimulator simulator = new SensorSimulator(sensors, controller, options.getIntervalMillis());
        String message = String.format("Simulating %d %s sensors, one reading every %d ms each (%d readings in total)",
                sensors.size(), options.getSensorType(), options.getIntervalMillis(), readings.size());
        audit.log(AuditLogger.SYSTEM, message);
        out.println(message);
        simulator.start();
        return simulator;
    }

    /** Starts the web dashboard on the configured port. */
    public DashboardServer startWebServer() throws IOException {
        DashboardServer server = new DashboardServer(controller, alertManager, invoker);
        server.start(options.getPort());
        audit.log(AuditLogger.SYSTEM, "Web dashboard started on port " + server.getPort());
        return server;
    }

    /** Creates the desktop dashboard window (not shown until {@link DesktopDashboard#show()}). */
    public DesktopDashboard createDesktopDashboard() {
        return new DesktopDashboard(controller, alertManager, invoker);
    }

    /**
     * Prints the end-of-run summary, each room's next-reading forecast from both models,
     * the accuracy of both prediction models on this data, and the audit-log integrity check.
     */
    public void printSummary(MonitoringReport report, List<SensorReading> readings) {
        consoleView.showReport(report);
        printModelComparison(report);
        printModelAccuracy(readings);
        audit.log(AuditLogger.SYSTEM, "Monitoring finished: " + report.getTotalReadings() + " readings, "
                + alertManager.getTotalAlerts() + " alerts");
        out.printf("%nAudit log: %d entries, integrity check: %s%n",
                audit.getEntries().size(), audit.verifyIntegrity() ? "PASSED" : "FAILED");
    }

    /** Uses {@link PredictionService} to forecast each room's next reading with both models. */
    private void printModelComparison(MonitoringReport report) {
        PredictionService service = new PredictionService(PredictionModel.LINEAR_REGRESSION.create(),
                controller.getChecker(), PredictionService.DEFAULT_HISTORY_SIZE);
        out.printf("%nNext-reading forecast by model (last %d readings per room):%n", service.getHistorySize());
        for (StorageUnit unit : report.getUnits()) {
            StringBuilder line = new StringBuilder(String.format("  %-18s", unit.getRoomId()));
            for (PredictionModel model : PredictionModel.values()) {
                service.setPredictor(model.create());
                service.forecast(unit, 1).ifPresent(f -> line.append(String.format("  %s: %6.2f C %-15s",
                        model.getDisplayName(), f.predictedTemperature(), "(" + f.predictedStatus().getLabel() + ")")));
            }
            out.println(line);
        }
    }

    private void printModelAccuracy(List<SensorReading> readings) {
        int steps = controller.getForecaster().getStepsAhead();
        out.printf("%nPrediction accuracy on this data (mean absolute error, %d readings ahead):%n", steps);
        for (PredictionModel model : PredictionModel.values()) {
            try {
                double mae = PredictorEvaluator.meanAbsoluteErrorByRoom(model.create(), readings, steps);
                out.printf("  %-18s %.2f C%n", model.getDisplayName(), mae);
            } catch (IllegalArgumentException e) {
                out.printf("  %-18s not enough data%n", model.getDisplayName());
            }
        }
    }

    /** Splits readings by room, keeping their original order. */
    static Map<String, List<SensorReading>> groupByRoom(List<SensorReading> readings) {
        Map<String, List<SensorReading>> byRoom = new LinkedHashMap<>();
        for (SensorReading reading : readings) {
            byRoom.computeIfAbsent(reading.getRoomId(), id -> new ArrayList<>()).add(reading);
        }
        return byRoom;
    }

    public MonitorController getController() {
        return controller;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public CommandInvoker getInvoker() {
        return invoker;
    }

    public ConsoleAlertView getConsoleView() {
        return consoleView;
    }

    public AppOptions getOptions() {
        return options;
    }
}

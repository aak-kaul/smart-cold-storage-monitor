package edu.northeastern.coldstorage;

import edu.northeastern.coldstorage.app.AppOptions;
import edu.northeastern.coldstorage.app.MonitoringSystem;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.sensor.SensorSimulator;
import edu.northeastern.coldstorage.view.desktop.DesktopDashboard;
import edu.northeastern.coldstorage.view.web.DashboardServer;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Examples (with Maven: {@code mvn compile exec:java -Dexec.args="..."}):
 * <pre>
 *   (no arguments)        batch: process IoT.csv at once and print a summary
 *   --mode=simulate       replay readings live through sensor threads
 *   --mode=web            live simulation + web dashboard at http://localhost:8080
 *   --mode=gui            live simulation + desktop dashboard window
 *   --sensor=spike        add temperature spikes to trigger alerts (demo)
 *   --help                list every option
 * </pre>
 */
public final class Main {

    private static final Path AUDIT_LOG = Path.of("logs", "audit.log");

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        AppOptions options;
        try {
            options = AppOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(AppOptions.usage());
            System.exit(2);
            return;
        }
        if (options.isHelp()) {
            System.out.println(AppOptions.usage());
            return;
        }

        AuditLogger audit = AuditLogger.getInstance();
        audit.writeTo(AUDIT_LOG);
        Runtime.getRuntime().addShutdownHook(new Thread(audit::close)); // also runs on Ctrl+C
        audit.log(AuditLogger.SYSTEM, "Smart Cold Storage Monitor started in " + options.getMode() + " mode");
        System.out.println("Smart Cold Storage Monitor (" + options.getMode().name().toLowerCase() + " mode)");

        MonitoringSystem system = new MonitoringSystem(options, System.out);
        List<SensorReading> readings;
        try {
            readings = system.loadReadings();
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not load dataset: " + e.getMessage());
            audit.log(AuditLogger.SYSTEM, "Dataset load failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        switch (options.getMode()) {
            case SIMULATE:
                runSimulation(system, readings);
                break;
            case WEB:
                runWeb(system, readings);
                break;
            case GUI:
                runGui(system, readings);
                break;
            case BATCH:
            default:
                system.printSummary(system.runBatch(readings), readings);
                System.out.println("Audit log written to " + AUDIT_LOG);
        }
    }

    /** Replays the data live and prints the summary when every sensor is done. */
    private static void runSimulation(MonitoringSystem system, List<SensorReading> readings)
            throws InterruptedException {
        SensorSimulator simulator = system.startSimulation(readings);
        simulator.awaitCompletion(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        simulator.stop();
        system.printSummary(system.getController().buildReport(), readings);
    }

    /** Starts the web dashboard and the live simulation; keeps serving until Ctrl+C. */
    private static void runWeb(MonitoringSystem system, List<SensorReading> readings) throws InterruptedException {
        DashboardServer server;
        try {
            server = system.startWebServer();
        } catch (IOException e) {
            System.err.println("Could not start the web dashboard on port " + system.getOptions().getPort()
                    + " (" + e.getMessage() + "). Try another port, e.g. --port=8081");
            System.exit(1);
            return;
        }
        System.out.println("Web dashboard: http://localhost:" + server.getPort() + "  (press Ctrl+C to stop)");
        SensorSimulator simulator = system.startSimulation(readings);
        simulator.awaitCompletion(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        simulator.stop();
        system.printSummary(system.getController().buildReport(), readings);
        System.out.println("Simulation finished. The dashboard stays open until you press Ctrl+C.");
        new CountDownLatch(1).await(); // wait forever; Ctrl+C ends the program
    }

    /** Opens the desktop dashboard and runs the live simulation until the window is closed. */
    private static void runGui(MonitoringSystem system, List<SensorReading> readings) throws InterruptedException {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No screen is available for the desktop window. Use --mode=web instead.");
            System.exit(1);
            return;
        }
        SensorSimulator simulator = system.startSimulation(readings);
        CountDownLatch closed = new CountDownLatch(1);
        DesktopDashboard dashboard = system.createDesktopDashboard();
        dashboard.onClose(closed::countDown);
        dashboard.show();
        System.out.println("Close the window to stop.");
        closed.await();
        simulator.stop();
        if (system.getController().hasReadings()) {
            system.printSummary(system.getController().buildReport(), readings);
        }
        System.exit(0); // Swing keeps its own threads alive; exit explicitly
    }
}

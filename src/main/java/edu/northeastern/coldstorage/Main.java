package edu.northeastern.coldstorage;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.data.ColdStorageDataReader;
import edu.northeastern.coldstorage.logging.AuditLogger;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.view.ConsoleAlertView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point. Runs the core use case end to end:
 * load readings, check each against the safe range, alert on unsafe ones,
 * record violations in the audit log, and print a summary.
 *
 * <p>Usage: no arguments reads src/main/resources/IoT.csv;
 * or pass a path to another CSV in the same format.
 */
public final class Main {

    private static final String DEFAULT_DATASET = "IoT.csv";
    private static final Path AUDIT_LOG = Path.of("logs", "audit.log");
    private static final int ALERTS_TO_PRINT = 15;

    private Main() {
    }

    public static void main(String[] args) {
        AuditLogger audit = AuditLogger.getInstance();
        audit.writeTo(AUDIT_LOG);
        audit.log(AuditLogger.SYSTEM, "Monitoring started");

        ColdStorageDataReader reader = new ColdStorageDataReader();
        List<SensorReading> readings;
        String source = args.length > 0 ? args[0] : DEFAULT_DATASET;
        try {
            readings = args.length > 0
                    ? reader.readFromFile(Path.of(args[0]))
                    : reader.readFromResource(DEFAULT_DATASET);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not load dataset: " + e.getMessage());
            audit.log(AuditLogger.SYSTEM, "Dataset load failed: " + e.getMessage());
            audit.close();
            System.exit(1);
            return;
        }
        audit.log(AuditLogger.SYSTEM, "Loaded " + readings.size() + " readings from " + source
                + " (" + reader.getSkippedRowCount() + " invalid rows skipped)");

        ComplianceChecker checker = new ComplianceChecker();
        System.out.println("Smart Cold Storage Monitor");
        System.out.printf("Loaded %d readings from %s (%d invalid rows skipped)%n",
                readings.size(), source, reader.getSkippedRowCount());
        System.out.printf("Safe range: %.1f to %.1f C, warning margin +/- %.1f C%n%n",
                checker.getMinSafe(), checker.getMaxSafe(), checker.getWarningMargin());

        ConsoleAlertView view = new ConsoleAlertView(System.out, ALERTS_TO_PRINT);
        MonitorController controller = new MonitorController(checker);
        controller.addListener(view);
        controller.addListener(audit);

        MonitoringReport report = controller.processAll(readings);
        view.showReport(report);

        audit.log(AuditLogger.SYSTEM, "Monitoring finished: " + report.getTotalReadings() + " readings, "
                + view.getAlertsReceived() + " alerts");
        System.out.printf("%nAudit log: %d entries written to %s, integrity check: %s%n",
                audit.getEntries().size(), AUDIT_LOG, audit.verifyIntegrity() ? "PASSED" : "FAILED");
        audit.close();
    }
}
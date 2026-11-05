package edu.northeastern.coldstorage.view;

import edu.northeastern.coldstorage.controller.AlertListener;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Text "dashboard" for the console. Prints alerts as they arrive (Observer pattern)
 * and a summary report at the end. A JavaFX/Swing view will replace this later.
 */
public class ConsoleAlertView implements AlertListener {

    private final PrintStream out;
    private final int maxAlertsToPrint;
    private int alertsReceived;

    /**
     * Creates a console view.
     *
     * @param out              where to print, usually System.out
     * @param maxAlertsToPrint print at most this many alerts (the rest are only counted)
     */
    public ConsoleAlertView(PrintStream out, int maxAlertsToPrint) {
        this.out = Objects.requireNonNull(out, "out must not be null");
        if (maxAlertsToPrint < 0) {
            throw new IllegalArgumentException("maxAlertsToPrint must not be negative");
        }
        this.maxAlertsToPrint = maxAlertsToPrint;
    }

    @Override
    public void onAlert(Alert alert) {
        alertsReceived++;
        if (alertsReceived <= maxAlertsToPrint) {
            out.println("ALERT " + alert.getMessage() + " -> " + alert.getStatus().getColour());
        }
    }

    /** Returns how many alerts this view has been notified about. */
    public int getAlertsReceived() {
        return alertsReceived;
    }

    /** Prints the end-of-run summary. */
    public void showReport(MonitoringReport report) {
        Objects.requireNonNull(report, "report must not be null");
        if (alertsReceived > maxAlertsToPrint) {
            out.printf("... and %d more alerts (see the audit log)%n", alertsReceived - maxAlertsToPrint);
        }
        out.println();
        out.println("================ MONITORING SUMMARY ================");
        out.printf("Readings processed : %d%n", report.getTotalReadings());
        for (ComplianceStatus s : ComplianceStatus.values()) {
            int count = report.getCount(s);
            out.printf("  %-14s (%-6s): %5d  (%.1f%%)%n", s.getLabel(), s.getColour(), count,
                    100.0 * count / report.getTotalReadings());
        }
        out.printf("Mean temperature   : %.2f C%n", report.getMeanTemperature());
        out.printf("Variance           : %.2f C^2%n", report.getVariance());
        out.println();
        out.println("Per storage unit (current state):");
        out.printf("  %-16s %8s  %-14s %7s %9s %7s %7s%n",
                "Room", "Current", "Status", "Mean", "Variance", "Min", "Max");
        for (StorageUnit unit : report.getUnits()) {
            out.printf("  %-16s %7.2f C %-14s %7.2f %9.2f %7.2f %7.2f%n",
                    unit.getRoomId(), unit.getCurrentTemperature(), unit.getCurrentStatus().getLabel(),
                    unit.getMean(), unit.getVariance(), unit.getMinTemperature(), unit.getMaxTemperature());
        }
        out.println();
        out.printf("Contractor (TMC) compliance: %s (%.1f%% non-compliant, limit %.1f%%)%n",
                report.isContractorCompliant() ? "PASS" : "FAIL",
                report.getPercentNonCompliant(), report.getContractorThresholdPercent());
        out.println("====================================================");
    }
}
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

    /** Width of the text bar chart in the status breakdown. */
    private static final int BAR_WIDTH = 30;
    private static final String DIVIDER =
            "======================================================================================";
    private static final String THIN_DIVIDER =
            "--------------------------------------------------------------------------------------";

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

    /** Prints the end-of-run summary in three sections: overview, status breakdown, rooms. */
    public void showReport(MonitoringReport report) {
        Objects.requireNonNull(report, "report must not be null");
        if (alertsReceived > maxAlertsToPrint) {
            out.printf("... and %d more alerts (see the audit log)%n", alertsReceived - maxAlertsToPrint);
        }
        out.println();
        out.println(DIVIDER);
        out.println("                              MONITORING SUMMARY");
        out.println(DIVIDER);
        printOverview(report);
        printStatusBreakdown(report);
        printRoomTable(report);
        printContractorResult(report);
        out.println(DIVIDER);
    }

    private void printOverview(MonitoringReport report) {
        out.printf("Readings processed : %d%n", report.getTotalReadings());
        out.printf("Alerts raised      : %d%n", alertsReceived);
        out.printf("Mean temperature   : %.2f C%n", report.getMeanTemperature());
        out.printf("Variance           : %.2f C^2%n", report.getVariance());
    }

    private void printStatusBreakdown(MonitoringReport report) {
        out.println();
        out.println("STATUS BREAKDOWN");
        out.println(THIN_DIVIDER);
        for (ComplianceStatus s : ComplianceStatus.values()) {
            int count = report.getCount(s);
            double percent = 100.0 * count / report.getTotalReadings();
            out.printf("  %-14s %-8s %5d  %5.1f%%  %s%n",
                    s.getLabel(), "(" + s.getColour() + ")", count, percent, bar(percent));
        }
    }

    private void printRoomTable(MonitoringReport report) {
        out.println();
        out.println("STORAGE UNITS (current state and statistics, C)");
        out.println(THIN_DIVIDER);
        out.printf("  %-16s %8s  %-14s %6s %8s %7s %7s %6s %6s %6s%n",
                "Room", "Current", "Status", "Mean", "Variance", "Min", "Max", "Green", "Yellow", "Red");
        for (StorageUnit unit : report.getUnits()) {
            out.printf("  %-16s %8.2f  %-14s %6.2f %8.2f %7.2f %7.2f %6d %6d %6d%n",
                    unit.getRoomId(), unit.getCurrentTemperature(), unit.getCurrentStatus().getLabel(),
                    unit.getMean(), unit.getVariance(), unit.getMinTemperature(), unit.getMaxTemperature(),
                    unit.getCount(ComplianceStatus.COMPLIANT),
                    unit.getCount(ComplianceStatus.WARNING),
                    unit.getCount(ComplianceStatus.NON_COMPLIANT));
        }
    }

    private void printContractorResult(MonitoringReport report) {
        out.println();
        out.printf("Contractor (TMC) compliance: %s (%.1f%% non-compliant, limit %.1f%%)%n",
                report.isContractorCompliant() ? "PASS" : "FAIL",
                report.getPercentNonCompliant(), report.getContractorThresholdPercent());
    }

    /** Builds a text bar such as "#########....." for a percentage from 0 to 100. */
    static String bar(double percent) {
        double clamped = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round(clamped / 100.0 * BAR_WIDTH);
        return "#".repeat(filled) + ".".repeat(BAR_WIDTH - filled);
    }
}

package edu.northeastern.coldstorage.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable summary of one monitoring run.
 */
public final class MonitoringReport {

    private final int totalReadings;
    private final Map<ComplianceStatus, Integer> statusCounts;
    private final double meanTemperature;
    private final double variance;
    private final double percentNonCompliant;
    private final double contractorThresholdPercent;
    private final List<StorageUnit> units;

    /**
     * Creates a report.
     *
     * @param totalReadings              number of readings processed
     * @param statusCounts               count of readings per status
     * @param meanTemperature            mean temperature across all readings
     * @param variance                   population variance across all readings
     * @param percentNonCompliant        percent of readings that were NON_COMPLIANT
     * @param contractorThresholdPercent max percent of unsafe readings the contractor may have
     * @param units                      per-room state, in display order
     */
    public MonitoringReport(int totalReadings, Map<ComplianceStatus, Integer> statusCounts,
                            double meanTemperature, double variance, double percentNonCompliant,
                            double contractorThresholdPercent, List<StorageUnit> units) {
        this.totalReadings = totalReadings;
        this.statusCounts = Collections.unmodifiableMap(new EnumMap<>(statusCounts));
        this.meanTemperature = meanTemperature;
        this.variance = variance;
        this.percentNonCompliant = percentNonCompliant;
        this.contractorThresholdPercent = contractorThresholdPercent;
        this.units = List.copyOf(units);
    }

    public int getTotalReadings() {
        return totalReadings;
    }

    /** Returns how many readings had the given status. */
    public int getCount(ComplianceStatus status) {
        return statusCounts.getOrDefault(status, 0);
    }

    public double getMeanTemperature() {
        return meanTemperature;
    }

    public double getVariance() {
        return variance;
    }

    public double getPercentNonCompliant() {
        return percentNonCompliant;
    }

    public double getContractorThresholdPercent() {
        return contractorThresholdPercent;
    }

    /** True if the maintenance contractor met the standard (unsafe readings at or under the threshold). */
    public boolean isContractorCompliant() {
        return percentNonCompliant <= contractorThresholdPercent;
    }

    public List<StorageUnit> getUnits() {
        return units;
    }
}
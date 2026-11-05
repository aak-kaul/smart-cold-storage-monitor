package edu.northeastern.coldstorage.ml;

import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.SensorReading;
import java.util.List;
import java.util.Objects;

/**
 * Classifies temperatures against the safe storage range.
 *
 * <ul>
 *   <li>COMPLIANT: inside [minSafe, maxSafe] (default 2.0–4.0 °C)</li>
 *   <li>WARNING: outside the safe range by at most warningMargin (default 1.0 °C,
 *       so 1.0–2.0 °C or 4.0–5.0 °C, the proposal's "breach limit")</li>
 *   <li>NON_COMPLIANT: further outside than that</li>
 * </ul>
 */
public class ComplianceChecker {

    public static final double DEFAULT_MIN_SAFE = 2.0;
    public static final double DEFAULT_MAX_SAFE = 4.0;
    public static final double DEFAULT_WARNING_MARGIN = 1.0;
    /** The contractor (TMC) fails if more than this percent of readings are non-compliant. */
    public static final double DEFAULT_CONTRACTOR_THRESHOLD_PERCENT = 5.0;

    private final double minSafe;
    private final double maxSafe;
    private final double warningMargin;

    /** Creates a checker with the proposal's limits: 2.0–4.0 °C, 1.0 °C warning margin. */
    public ComplianceChecker() {
        this(DEFAULT_MIN_SAFE, DEFAULT_MAX_SAFE, DEFAULT_WARNING_MARGIN);
    }

    /**
     * Creates a checker with custom limits.
     *
     * @param minSafe       lowest safe temperature (inclusive)
     * @param maxSafe       highest safe temperature (inclusive)
     * @param warningMargin how far outside the safe range still counts as a warning
     * @throws IllegalArgumentException if minSafe &gt; maxSafe or warningMargin is negative
     */
    public ComplianceChecker(double minSafe, double maxSafe, double warningMargin) {
        if (minSafe > maxSafe) {
            throw new IllegalArgumentException("minSafe must not be greater than maxSafe");
        }
        if (warningMargin < 0) {
            throw new IllegalArgumentException("warningMargin must not be negative");
        }
        this.minSafe = minSafe;
        this.maxSafe = maxSafe;
        this.warningMargin = warningMargin;
    }

    /** Classifies a single temperature in °C. */
    public ComplianceStatus classify(double temperature) {
        if (temperature >= minSafe && temperature <= maxSafe) {
            return ComplianceStatus.COMPLIANT;
        }
        if (temperature >= minSafe - warningMargin && temperature <= maxSafe + warningMargin) {
            return ComplianceStatus.WARNING;
        }
        return ComplianceStatus.NON_COMPLIANT;
    }

    /** Classifies a reading by its temperature. */
    public ComplianceStatus classify(SensorReading reading) {
        Objects.requireNonNull(reading, "reading must not be null");
        return classify(reading.getTemperature());
    }

    /**
     * Returns the percent (0–100) of readings that are NON_COMPLIANT.
     *
     * @throws IllegalArgumentException if the list is empty
     */
    public double percentNonCompliant(List<SensorReading> readings) {
        Objects.requireNonNull(readings, "readings must not be null");
        if (readings.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a percentage of zero readings");
        }
        long unsafe = readings.stream()
                .filter(r -> classify(r) == ComplianceStatus.NON_COMPLIANT)
                .count();
        return 100.0 * unsafe / readings.size();
    }

    /** Returns true if non-compliant readings are at or below the 5% contractor threshold. */
    public boolean isContractorCompliant(List<SensorReading> readings) {
        return percentNonCompliant(readings) <= DEFAULT_CONTRACTOR_THRESHOLD_PERCENT;
    }

    public double getMinSafe() {
        return minSafe;
    }

    public double getMaxSafe() {
        return maxSafe;
    }

    public double getWarningMargin() {
        return warningMargin;
    }
}
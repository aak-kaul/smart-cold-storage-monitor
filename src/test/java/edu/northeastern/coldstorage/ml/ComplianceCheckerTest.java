package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.SensorReading;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComplianceCheckerTest {

    private ComplianceChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ComplianceChecker();
    }

    @Test
    void insideSafeRangeIsCompliant() {
        assertEquals(ComplianceStatus.COMPLIANT, checker.classify(3.0));
    }

    @Test
    void safeRangeBoundariesAreCompliant() {
        assertEquals(ComplianceStatus.COMPLIANT, checker.classify(2.0));
        assertEquals(ComplianceStatus.COMPLIANT, checker.classify(4.0));
    }

    @Test
    void slightlyOutsideRangeIsWarning() {
        assertEquals(ComplianceStatus.WARNING, checker.classify(4.6));
        assertEquals(ComplianceStatus.WARNING, checker.classify(1.5));
        assertEquals(ComplianceStatus.WARNING, checker.classify(5.0));
    }

    @Test
    void farOutsideRangeIsNonCompliant() {
        assertEquals(ComplianceStatus.NON_COMPLIANT, checker.classify(5.1));
        assertEquals(ComplianceStatus.NON_COMPLIANT, checker.classify(0.9));
        assertEquals(ComplianceStatus.NON_COMPLIANT, checker.classify(18.9));
        assertEquals(ComplianceStatus.NON_COMPLIANT, checker.classify(-3.9));
    }

    @Test
    void customLimitsAreUsed() {
        ComplianceChecker wide = new ComplianceChecker(2.0, 8.0, 0.0);
        assertEquals(ComplianceStatus.COMPLIANT, wide.classify(7.5));
        assertEquals(ComplianceStatus.NON_COMPLIANT, wide.classify(8.1));
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ComplianceChecker(5.0, 2.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ComplianceChecker(2.0, 4.0, -1.0));
    }

    @Test
    void percentNonCompliantCountsOnlyNonCompliantReadings() {
        List<SensorReading> readings = List.of(reading(3.0), reading(4.5), reading(10.0), reading(-2.0));
        // 10.0 and -2.0 are non-compliant; 4.5 is only a warning.
        assertEquals(50.0, checker.percentNonCompliant(readings), 1e-9);
    }

    @Test
    void contractorPassesAtExactlyFivePercent() {
        List<SensorReading> readings = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            readings.add(reading(3.0));
        }
        readings.add(reading(12.0)); // 1 of 20 = 5%
        assertTrue(checker.isContractorCompliant(readings));

        readings.add(reading(12.0)); // 2 of 21 ≈ 9.5%
        assertFalse(checker.isContractorCompliant(readings));
    }

    @Test
    void percentOfEmptyListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> checker.percentNonCompliant(List.of()));
    }

    private static SensorReading reading(double temperature) {
        return new SensorReading(1, "Cold_Storage_A", LocalDateTime.of(2024, 1, 1, 0, 0), temperature, 85.0);
    }
}
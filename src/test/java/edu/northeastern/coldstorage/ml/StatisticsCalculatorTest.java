package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsCalculatorTest {

    private static final List<Double> VALUES = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);

    @Test
    void meanOfKnownValues() {
        assertEquals(5.0, StatisticsCalculator.mean(VALUES), 1e-9);
    }

    @Test
    void varianceAndStandardDeviationOfKnownValues() {
        assertEquals(4.0, StatisticsCalculator.variance(VALUES), 1e-9);
        assertEquals(2.0, StatisticsCalculator.standardDeviation(VALUES), 1e-9);
    }

    @Test
    void singleValueHasZeroVariance() {
        assertEquals(0.0, StatisticsCalculator.variance(List.of(3.3)), 1e-9);
    }

    @Test
    void emptyListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> StatisticsCalculator.mean(List.of()));
    }
}
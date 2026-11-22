package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.Forecast;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TemperatureForecasterTest {

    private final ComplianceChecker checker = new ComplianceChecker();

    @Test
    void forecastsARoomWarmingTowardsUnsafe() {
        // 2.5, 3.0, 3.5, 4.0: rising 0.5 per reading -> three ahead is 5.5 C (non-compliant)
        StorageUnit unit = unitWith(2.5, 3.0, 3.5, 4.0);
        Forecast forecast = linearForecaster().forecast(unit).orElseThrow();

        assertEquals("Cold_Storage_A", forecast.roomId());
        assertEquals(5.5, forecast.predictedTemperature(), 1e-9);
        assertEquals(ComplianceStatus.NON_COMPLIANT, forecast.predictedStatus());
        assertTrue(forecast.isRiskPredicted());
        assertEquals("Linear regression", forecast.modelName());
        assertEquals(3, forecast.stepsAhead());
    }

    @Test
    void stableRoomHasNoPredictedRisk() {
        Forecast forecast = new TemperatureForecaster(checker).forecast(unitWith(3.0, 3.0, 3.0)).orElseThrow();
        assertEquals(ComplianceStatus.COMPLIANT, forecast.predictedStatus());
        assertFalse(forecast.isRiskPredicted());
    }

    @Test
    void noForecastWithoutEnoughHistory() {
        // Linear regression needs two readings; one is not enough.
        assertEquals(Optional.empty(), linearForecaster().forecast(unitWith(3.0)));
    }

    @Test
    void defaultModelIsMovingAverage() {
        Forecast forecast = new TemperatureForecaster(checker).forecast(unitWith(2.5, 3.0, 3.5, 4.0)).orElseThrow();
        assertEquals("Moving average", forecast.modelName());
        assertEquals(3.25, forecast.predictedTemperature(), 1e-9);
        assertEquals(ComplianceStatus.COMPLIANT, forecast.predictedStatus());
    }

    @Test
    void strategyCanBeSwappedAtRuntime() {
        TemperatureForecaster forecaster = new TemperatureForecaster(checker);
        StorageUnit unit = unitWith(2.5, 3.0, 3.5, 4.0);

        forecaster.setPredictor(new LinearRegressionPredictor());
        Forecast forecast = forecaster.forecast(unit).orElseThrow();

        assertEquals("Linear regression", forecast.modelName());
        assertEquals(5.5, forecast.predictedTemperature(), 1e-9);
        assertTrue(forecast.isRiskPredicted());
    }

    @Test
    void customStepsAheadIsUsed() {
        TemperatureForecaster forecaster = new TemperatureForecaster(checker, new LinearRegressionPredictor(), 1);
        assertEquals(4.5, forecaster.forecast(unitWith(2.5, 3.0, 3.5, 4.0)).orElseThrow().predictedTemperature(),
                1e-9);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new TemperatureForecaster(checker, new MovingAveragePredictor(), 0));
        assertThrows(NullPointerException.class, () -> new TemperatureForecaster(checker).setPredictor(null));
    }

    private TemperatureForecaster linearForecaster() {
        return new TemperatureForecaster(checker, new LinearRegressionPredictor(),
                TemperatureForecaster.DEFAULT_STEPS_AHEAD);
    }

    private static StorageUnit unitWith(double... temperatures) {
        StorageUnit unit = new StorageUnit("Cold_Storage_A");
        ComplianceChecker rules = new ComplianceChecker();
        for (int i = 0; i < temperatures.length; i++) {
            SensorReading r = new SensorReading(i, "Cold_Storage_A",
                    LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i), temperatures[i], 85.0);
            unit.record(r, rules.classify(r));
        }
        return unit;
    }
}

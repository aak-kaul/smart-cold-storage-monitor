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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PredictionServiceTest {

    private static final double EPS = 1e-9;
    private static final String ROOM_A = "Cold_Storage_A";
    private static final String ROOM_B = "Cold_Storage_B";
    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 1, 0, 0);

    private PredictionService service;
    private int nextId;

    @BeforeEach
    void setUp() {
        service = new PredictionService(new LinearRegressionPredictor(), new ComplianceChecker(), 4);
        nextId = 1;
    }

    private SensorReading reading(String room, double temperature) {
        int id = nextId++;
        return new SensorReading(id, room, START.plusHours(id), temperature, 50.0);
    }

    private void recordAll(String room, double... temperatures) {
        for (double t : temperatures) {
            service.record(reading(room, t));
        }
    }

    @Test
    void unknownRoomHasNoPrediction() {
        assertTrue(service.predict(ROOM_A, 1).isEmpty());
        assertTrue(service.predictStatus(ROOM_A, 1).isEmpty());
    }

    @Test
    void predictsUsingCurrentStrategy() {
        recordAll(ROOM_A, 2.0, 2.5, 3.0, 3.5);
        assertEquals(4.0, service.predict(ROOM_A, 1).getAsDouble(), EPS);
    }

    @Test
    void strategyCanBeSwappedAtRuntime() {
        recordAll(ROOM_A, 2.0, 2.5, 3.0, 3.5);
        service.setPredictor(new MovingAveragePredictor(4));
        assertEquals(2.75, service.predict(ROOM_A, 1).getAsDouble(), EPS);
        assertEquals("Moving average", service.getPredictor().getName());
    }

    @Test
    void historyKeepsOnlyMostRecentReadings() {
        // history size is 4, so the early 50.0 readings must be dropped
        recordAll(ROOM_A, 50.0, 50.0, 3.0, 3.0, 3.0, 3.0);
        assertEquals(4, service.getHistoryCount(ROOM_A));
        assertEquals(3.0, service.predict(ROOM_A, 1).getAsDouble(), EPS);
    }

    @Test
    void roomsAreForecastSeparately() {
        recordAll(ROOM_A, 3.0, 3.0, 3.0);
        recordAll(ROOM_B, 8.0, 8.0, 8.0);
        assertEquals(3.0, service.predict(ROOM_A, 1).getAsDouble(), EPS);
        assertEquals(8.0, service.predict(ROOM_B, 1).getAsDouble(), EPS);
        assertEquals(2, service.getRoomIds().size());
    }

    @Test
    void risingRoomIsFlaggedBeforeItLeavesSafeRange() {
        // currently safe (3.0 to 3.9), but the trend reaches 4.2 next: WARNING ahead
        recordAll(ROOM_A, 3.0, 3.3, 3.6, 3.9);
        assertEquals(ComplianceStatus.COMPLIANT, new ComplianceChecker().classify(3.9));
        assertEquals(ComplianceStatus.WARNING, service.predictStatus(ROOM_A, 1).get());
        // further ahead the trend reaches 5.7, which is outside the warning margin
        assertEquals(ComplianceStatus.NON_COMPLIANT, service.predictStatus(ROOM_A, 6).get());
    }

    @Test
    void forecastsDirectlyFromStorageUnitHistory() {
        StorageUnit unit = new StorageUnit(ROOM_A);
        for (double t : new double[] {2.0, 2.5, 3.0, 3.5}) {
            unit.record(reading(ROOM_A, t), ComplianceStatus.COMPLIANT);
        }
        Forecast next = service.forecast(unit, 1).get();
        assertEquals(ROOM_A, next.roomId());
        assertEquals("Linear regression", next.modelName());
        assertEquals(1, next.stepsAhead());
        assertEquals(4.0, next.predictedTemperature(), EPS);
        assertEquals(ComplianceStatus.COMPLIANT, next.predictedStatus());
        assertEquals(ComplianceStatus.WARNING, service.forecast(unit, 2).get().predictedStatus());
    }

    @Test
    void forecastFromUnitUsesOnlyMostRecentHistory() {
        // service history size is 4, so the early 50.0 readings must be ignored
        StorageUnit unit = new StorageUnit(ROOM_A);
        for (double t : new double[] {50.0, 50.0, 3.0, 3.0, 3.0, 3.0}) {
            unit.record(reading(ROOM_A, t), ComplianceStatus.COMPLIANT);
        }
        assertEquals(3.0, service.forecast(unit, 1).get().predictedTemperature(), EPS);
    }

    @Test
    void emptyStorageUnitHasNoForecast() {
        StorageUnit unit = new StorageUnit(ROOM_A);
        assertTrue(service.forecast(unit, 1).isEmpty());
    }

    @Test
    void invalidArgumentsAreRejected() {
        ComplianceChecker checker = new ComplianceChecker();
        LinearRegressionPredictor model = new LinearRegressionPredictor();
        assertThrows(NullPointerException.class, () -> new PredictionService(null, checker, 4));
        assertThrows(IllegalArgumentException.class, () -> new PredictionService(model, checker, 1));
        assertThrows(NullPointerException.class, () -> service.setPredictor(null));
        recordAll(ROOM_A, 3.0);
        assertThrows(IllegalArgumentException.class, () -> service.predict(ROOM_A, 0));
    }

    @Test
    void concurrentRecordingKeepsHistoryConsistent() throws InterruptedException {
        PredictionService shared = new PredictionService(
                new MovingAveragePredictor(100), new ComplianceChecker(), 100);
        List<SensorReading> readings = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            readings.add(new SensorReading(i, ROOM_A, START.plusMinutes(i), 3.0, 50.0));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (SensorReading r : readings) {
            pool.submit(() -> shared.record(r));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(100, shared.getHistoryCount(ROOM_A));
        assertEquals(3.0, shared.predict(ROOM_A, 1).getAsDouble(), EPS);
        assertFalse(shared.getRoomIds().isEmpty());
    }
}

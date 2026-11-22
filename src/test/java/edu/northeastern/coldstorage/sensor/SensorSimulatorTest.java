package edu.northeastern.coldstorage.sensor;

import static org.junit.jupiter.api.Assertions.*;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.SensorReading;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SensorSimulatorTest {

    /** Integration test: concurrent sensors -> controller -> compliance check -> alerts. */
    @Test
    void threeConcurrentSensorsProcessEveryReadingAndRaiseSpikeAlerts() throws Exception {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        AtomicInteger alerts = new AtomicInteger();
        controller.addListener(alert -> alerts.incrementAndGet());

        List<Sensor> sensors = List.of(
                SensorFactory.create(SensorType.CSV_REPLAY, "A", SensorFactoryTest.readings("A", 50, 3.0)),
                SensorFactory.create(SensorType.CSV_REPLAY, "B", SensorFactoryTest.readings("B", 50, 3.0)),
                SensorFactory.create(SensorType.SPIKE, "C", SensorFactoryTest.readings("C", 50, 3.0)));

        SensorSimulator simulator = new SensorSimulator(sensors, controller, 1);
        simulator.start();
        assertTrue(simulator.awaitCompletion(10, TimeUnit.SECONDS), "sensors did not finish in time");
        simulator.stop();

        assertEquals(150, simulator.getProcessedCount());
        assertEquals(50, controller.getUnit("A").getReadingCount());
        assertEquals(50, controller.getUnit("B").getReadingCount());
        assertEquals(50, controller.getUnit("C").getReadingCount());
        assertEquals(5, alerts.get()); // spike sensor: readings 10, 20, 30, 40, 50 at 6.0 C
    }

    /** Concurrency test: many threads writing to one room must not lose updates. */
    @Test
    void parallelProcessingDoesNotLoseReadings() throws Exception {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        List<SensorReading> data = SensorFactoryTest.readings("A", 1000, 3.0);
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                startGate.await(); // all threads start at the same moment
                for (SensorReading r : data) {
                    controller.process(r);
                }
                return null;
            });
        }
        startGate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(10_000, controller.getUnit("A").getReadingCount());
        assertEquals(10_000, controller.buildReport().getTotalReadings());
    }

    @Test
    void invalidArgumentsAreRejected() {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        assertThrows(IllegalArgumentException.class, () -> new SensorSimulator(List.of(), controller, 10));
        Sensor s = SensorFactory.create(SensorType.CSV_REPLAY, "A", SensorFactoryTest.readings("A", 1, 3.0));
        assertThrows(IllegalArgumentException.class, () -> new SensorSimulator(List.of(s), controller, 0));
    }
}
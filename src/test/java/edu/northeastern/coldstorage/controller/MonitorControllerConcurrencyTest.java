package edu.northeastern.coldstorage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Checks that several sensor threads can feed the controller at once without losing data. */
class MonitorControllerConcurrencyTest {

    private static final int THREADS = 8;
    private static final int READINGS_PER_THREAD = 500;

    @Test
    void concurrentReadingsAreAllCounted() throws InterruptedException {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        AtomicInteger alerts = new AtomicInteger();
        controller.addListener(alert -> alerts.incrementAndGet());

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < THREADS; t++) {
            String room = "Room_" + t;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < READINGS_PER_THREAD; i++) {
                    // every other reading is unsafe (9.0 C) so half of them raise alerts
                    double temp = i % 2 == 0 ? 3.0 : 9.0;
                    controller.process(new SensorReading(i, room,
                            LocalDateTime.of(2024, 1, 1, 0, 0).plusMinutes(i), temp, 80.0));
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        MonitoringReport report = controller.buildReport();
        int total = THREADS * READINGS_PER_THREAD;
        assertEquals(total, report.getTotalReadings());
        assertEquals(total / 2, report.getCount(ComplianceStatus.COMPLIANT));
        assertEquals(total / 2, report.getCount(ComplianceStatus.NON_COMPLIANT));
        assertEquals(total / 2, alerts.get());
        assertEquals(THREADS, report.getUnits().size());
        for (StorageUnit unit : report.getUnits()) {
            assertEquals(READINGS_PER_THREAD, unit.getReadingCount());
        }
    }

    @Test
    void reportsCanBeBuiltWhileReadingsArrive() throws InterruptedException {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        controller.process(reading(0));
        List<Throwable> errors = new ArrayList<>();

        Thread writer = new Thread(() -> {
            for (int i = 1; i <= 2000; i++) {
                controller.process(reading(i));
            }
        });
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    MonitoringReport report = controller.buildReport();
                    StorageUnit unit = report.getUnits().get(0);
                    // a copied unit must always be internally consistent
                    assertEquals(unit.getReadingCount(), unit.getCount(ComplianceStatus.COMPLIANT));
                }
            } catch (Throwable e) {
                synchronized (errors) {
                    errors.add(e);
                }
            }
        });
        writer.start();
        reader.start();
        writer.join();
        reader.join();

        assertTrue(errors.isEmpty(), () -> "reader saw inconsistent data: " + errors);
        assertEquals(2001, controller.getReadingCount());
    }

    @Test
    void returnedUnitIsACopy() {
        MonitorController controller = new MonitorController(new ComplianceChecker());
        controller.process(reading(1));
        StorageUnit copy = controller.getUnit("Room_A");
        copy.record(reading(2), ComplianceStatus.COMPLIANT);

        assertEquals(1, controller.getUnit("Room_A").getReadingCount());
    }

    private static SensorReading reading(int id) {
        return new SensorReading(id, "Room_A", LocalDateTime.of(2024, 1, 1, 0, 0).plusMinutes(id), 3.0, 80.0);
    }
}

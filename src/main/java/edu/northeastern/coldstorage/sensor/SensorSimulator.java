package edu.northeastern.coldstorage.sensor;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.model.SensorReading;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulates real-time sensors: every sensor sends one reading per interval,
 * all sensors run concurrently on a shared ScheduledExecutorService.
 */
public class SensorSimulator {
    private final List<Sensor> sensors;
    private final MonitorController controller;
    private final long intervalMillis;
    private final AtomicInteger processedCount = new AtomicInteger();
    private ScheduledExecutorService executor;
    private CountDownLatch finished;

    public SensorSimulator(List<Sensor> sensors, MonitorController controller, long intervalMillis) {
        if (sensors == null || sensors.isEmpty()) {
            throw new IllegalArgumentException("at least one sensor is required");
        }
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("intervalMillis must be positive");
        }
        this.sensors = List.copyOf(sensors);
        this.controller = Objects.requireNonNull(controller, "controller must not be null");
        this.intervalMillis = intervalMillis;
    }

    /** Starts all sensors. Each sensor gets its own repeating task. */
    public synchronized void start() {
        if (executor != null) {
            throw new IllegalStateException("simulator already started");
        }
        executor = Executors.newScheduledThreadPool(sensors.size());
        finished = new CountDownLatch(sensors.size());
        for (Sensor sensor : sensors) {
            scheduleSensor(sensor);
        }
    }

    private void scheduleSensor(Sensor sensor) {
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean(false);
        Runnable task = () -> {
            try {
                Optional<SensorReading> next = sensor.nextReading();
                if (next.isPresent()) {
                    controller.process(next.get());
                    processedCount.incrementAndGet();
                } else if (done.compareAndSet(false, true)) {
                    // sensor has no more data: report it once and stop its task
                    finished.countDown();
                    ScheduledFuture<?> f = future.get();
                    if (f != null) {
                        f.cancel(false);
                    }
                }
            } catch (RuntimeException e) {
                // an uncaught exception would silently stop this sensor's task forever
                System.err.println("Sensor " + sensor.getId() + " failed: " + e.getMessage());
            }
        };
        future.set(executor.scheduleAtFixedRate(task, 0, intervalMillis, TimeUnit.MILLISECONDS));
    }

    /** Waits until every sensor has run out of data. Returns false on timeout. */
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        if (finished == null) {
            throw new IllegalStateException("simulator not started");
        }
        return finished.await(timeout, unit);
    }

    /** Stops all sensors and shuts the thread pool down cleanly. */
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getProcessedCount() {
        return processedCount.get();
    }
}
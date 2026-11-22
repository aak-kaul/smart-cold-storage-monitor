package edu.northeastern.coldstorage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.app.AppOptions;
import edu.northeastern.coldstorage.app.MonitoringSystem;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import edu.northeastern.coldstorage.sensor.SensorSimulator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the real-time workflow: the dataset is replayed by one sensor
 * thread per room. Because the threads run at the same time, this also proves the
 * controller is thread-safe: the live run must give exactly the same results as the batch run.
 */
class SimulationWorkflowIntegrationTest {

    @Test
    void liveSimulationGivesTheSameResultsAsBatchProcessing() throws Exception {
        MonitoringSystem live = newSystem("--interval=1");
        List<SensorReading> readings = live.loadReadings().subList(0, 900); // ~300 per room keeps the test fast

        SensorSimulator simulator = live.startSimulation(readings);
        assertTrue(simulator.awaitCompletion(60, TimeUnit.SECONDS), "simulation should finish");
        MonitoringReport liveReport = live.getController().buildReport();

        MonitoringSystem batch = newSystem();
        MonitoringReport batchReport = batch.runBatch(readings);

        simulator.stop();
        assertEquals(readings.size(), simulator.getProcessedCount());
        assertEquals(batchReport.getTotalReadings(), liveReport.getTotalReadings());
        for (ComplianceStatus status : ComplianceStatus.values()) {
            assertEquals(batchReport.getCount(status), liveReport.getCount(status), status.name());
        }
        assertEquals(batchReport.getUnits().size(), liveReport.getUnits().size());
        for (int i = 0; i < batchReport.getUnits().size(); i++) {
            StorageUnit expected = batchReport.getUnits().get(i);
            StorageUnit actual = liveReport.getUnits().get(i);
            assertEquals(expected.getRoomId(), actual.getRoomId());
            assertEquals(expected.getReadingCount(), actual.getReadingCount());
            assertEquals(expected.getMean(), actual.getMean(), 1e-9);
            assertEquals(expected.getVariance(), actual.getVariance(), 1e-9);
        }
        assertEquals(live.getAlertManager().getTotalAlerts(), batch.getAlertManager().getTotalAlerts());
        // Each room is replayed in order by its own thread, so the forecasts match too.
        assertEquals(batchReport.getForecasts(), liveReport.getForecasts());
    }

    @Test
    void spikeSensorsTriggerExtraAlertsInTheLiveRun() throws Exception {
        MonitoringSystem replay = newSystem("--interval=1");
        List<SensorReading> readings = replay.loadReadings().subList(0, 300);
        runToEnd(replay, readings);

        MonitoringSystem spiky = newSystem("--interval=1", "--sensor=spike");
        runToEnd(spiky, readings);

        // Every 10th reading per room is 3.0 C hotter, so the spiky run raises more alerts.
        assertTrue(spiky.getAlertManager().getTotalAlerts() > replay.getAlertManager().getTotalAlerts());
    }

    private static void runToEnd(MonitoringSystem system, List<SensorReading> readings) throws Exception {
        SensorSimulator simulator = system.startSimulation(readings);
        try {
            assertTrue(simulator.awaitCompletion(60, TimeUnit.SECONDS), "simulation should finish");
        } finally {
            simulator.stop();
        }
    }

    @Test
    void readingsArriveGraduallyNotAllAtOnce() throws Exception {
        MonitoringSystem live = newSystem("--interval=20");
        List<SensorReading> readings = live.loadReadings();
        SensorSimulator simulator = live.startSimulation(readings);
        try {
            Thread.sleep(300);
            int soFar = live.getController().getReadingCount();
            // 3 sensors × ~15 ticks in 300 ms: some readings have arrived, but nowhere near all 4,977.
            assertTrue(soFar > 0 && soFar < 500, "readings so far: " + soFar);
        } finally {
            simulator.stop();
        }
    }

    private static MonitoringSystem newSystem(String... args) {
        String[] all = new String[args.length + 1];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = "--alerts=0";
        return new MonitoringSystem(AppOptions.parse(all),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
}

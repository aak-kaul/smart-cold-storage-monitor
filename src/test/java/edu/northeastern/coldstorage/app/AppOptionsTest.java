package edu.northeastern.coldstorage.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.coldstorage.ml.PredictionModel;
import edu.northeastern.coldstorage.sensor.SensorType;
import org.junit.jupiter.api.Test;

class AppOptionsTest {

    @Test
    void noArgumentsGiveTheDefaults() {
        AppOptions options = AppOptions.parse(new String[0]);
        assertEquals(AppOptions.Mode.BATCH, options.getMode());
        assertEquals("IoT.csv", options.getData());
        assertEquals(100, options.getIntervalMillis());
        assertEquals(PredictionModel.MOVING_AVERAGE, options.getPredictionModel());
        assertEquals(8080, options.getPort());
        assertEquals(15, options.getAlertsToPrint());
        assertEquals(SensorType.CSV_REPLAY, options.getSensorType());
        assertFalse(options.isHelp());
    }

    @Test
    void parsesEveryOption() {
        AppOptions options = AppOptions.parse(new String[] {
            "--mode=web", "--data=cold_storage_high_frequency.csv", "--interval=25",
            "--predictor=linear", "--port=9090", "--alerts=3", "--sensor=spike"});
        assertEquals(AppOptions.Mode.WEB, options.getMode());
        assertEquals("cold_storage_high_frequency.csv", options.getData());
        assertEquals(25, options.getIntervalMillis());
        assertEquals(PredictionModel.LINEAR_REGRESSION, options.getPredictionModel());
        assertEquals(9090, options.getPort());
        assertEquals(3, options.getAlertsToPrint());
        assertEquals(SensorType.SPIKE, options.getSensorType());
    }

    @Test
    void modeIsCaseInsensitive() {
        assertEquals(AppOptions.Mode.GUI, AppOptions.parse(new String[] {"--mode=GUI"}).getMode());
    }

    @Test
    void helpFlagIsRecognised() {
        assertTrue(AppOptions.parse(new String[] {"--help"}).isHelp());
        assertTrue(AppOptions.usage().contains("--mode"));
    }

    @Test
    void badArgumentsGiveClearErrors() {
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--mode=fast"}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--interval=0"}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--interval=abc"}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--port=70000"}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--colour=red"}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"web"}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--data="}));
        assertThrows(IllegalArgumentException.class, () -> AppOptions.parse(new String[] {"--sensor=laser"}));
    }
}

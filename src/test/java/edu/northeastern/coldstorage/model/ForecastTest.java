package edu.northeastern.coldstorage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ForecastTest {

    private static final String ROOM = "Cold_Storage_A";
    private static final String MODEL = "Linear regression";

    @Test
    void storesAllValues() {
        Forecast f = new Forecast(ROOM, MODEL, 2, 4.25, ComplianceStatus.WARNING);
        assertEquals(ROOM, f.getRoomId());
        assertEquals(MODEL, f.getModelName());
        assertEquals(2, f.getStepsAhead());
        assertEquals(4.25, f.getPredictedTemperature(), 1e-9);
        assertEquals(ComplianceStatus.WARNING, f.getPredictedStatus());
    }

    @Test
    void onlyCompliantForecastIsSafe() {
        assertFalse(new Forecast(ROOM, MODEL, 1, 3.0, ComplianceStatus.COMPLIANT).isUnsafe());
        assertTrue(new Forecast(ROOM, MODEL, 1, 4.5, ComplianceStatus.WARNING).isUnsafe());
        assertTrue(new Forecast(ROOM, MODEL, 1, 9.0, ComplianceStatus.NON_COMPLIANT).isUnsafe());
    }

    @Test
    void equalForecastsAreEqual() {
        Forecast a = new Forecast(ROOM, MODEL, 1, 3.5, ComplianceStatus.COMPLIANT);
        Forecast b = new Forecast(ROOM, MODEL, 1, 3.5, ComplianceStatus.COMPLIANT);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Forecast(ROOM, MODEL, 2, 3.5, ComplianceStatus.COMPLIANT));
    }

    @Test
    void toStringShowsRoomTemperatureAndModel() {
        String text = new Forecast(ROOM, MODEL, 1, 4.19, ComplianceStatus.WARNING).toString();
        assertTrue(text.contains(ROOM) && text.contains("4.19") && text.contains("WARNING") && text.contains(MODEL));
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> new Forecast(null, MODEL, 1, 3.0, ComplianceStatus.COMPLIANT));
        assertThrows(NullPointerException.class, () -> new Forecast(ROOM, MODEL, 1, 3.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Forecast(ROOM, MODEL, 0, 3.0, ComplianceStatus.COMPLIANT));
        assertThrows(IllegalArgumentException.class,
                () -> new Forecast(ROOM, MODEL, 1, Double.NaN, ComplianceStatus.COMPLIANT));
    }
}

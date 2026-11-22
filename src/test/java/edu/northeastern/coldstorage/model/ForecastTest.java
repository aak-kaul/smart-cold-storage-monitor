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
        Forecast f = new Forecast(ROOM, 4.25, ComplianceStatus.WARNING, MODEL, 2);
        assertEquals(ROOM, f.roomId());
        assertEquals(MODEL, f.modelName());
        assertEquals(2, f.stepsAhead());
        assertEquals(4.25, f.predictedTemperature(), 1e-9);
        assertEquals(ComplianceStatus.WARNING, f.predictedStatus());
    }

    @Test
    void onlyCompliantForecastIsSafe() {
        assertFalse(new Forecast(ROOM, 3.0, ComplianceStatus.COMPLIANT, MODEL, 1).isRiskPredicted());
        assertTrue(new Forecast(ROOM, 4.5, ComplianceStatus.WARNING, MODEL, 1).isRiskPredicted());
        assertTrue(new Forecast(ROOM, 9.0, ComplianceStatus.NON_COMPLIANT, MODEL, 1).isRiskPredicted());
    }

    @Test
    void equalForecastsAreEqual() {
        Forecast a = new Forecast(ROOM, 3.5, ComplianceStatus.COMPLIANT, MODEL, 1);
        Forecast b = new Forecast(ROOM, 3.5, ComplianceStatus.COMPLIANT, MODEL, 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Forecast(ROOM, 3.5, ComplianceStatus.COMPLIANT, MODEL, 2));
    }

    @Test
    void toStringShowsRoomTemperatureAndModel() {
        String text = new Forecast(ROOM, 4.19, ComplianceStatus.WARNING, MODEL, 1).toString();
        assertTrue(text.contains(ROOM) && text.contains("4.19") && text.contains("WARNING") && text.contains(MODEL));
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> new Forecast(null, 3.0, ComplianceStatus.COMPLIANT, MODEL, 1));
        assertThrows(NullPointerException.class, () -> new Forecast(ROOM, 3.0, null, MODEL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Forecast(ROOM, 3.0, ComplianceStatus.COMPLIANT, MODEL, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Forecast(ROOM, Double.NaN, ComplianceStatus.COMPLIANT, MODEL, 1));
    }
}

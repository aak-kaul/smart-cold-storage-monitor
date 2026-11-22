package edu.northeastern.coldstorage.view.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.Forecast;
import edu.northeastern.coldstorage.model.SensorReading;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the desktop dashboard's table data. These need no screen, so they run anywhere. */
class DesktopTableModelTest {

    @Test
    void roomTableShowsOneRowPerRoomWithForecast() {
        StorageUnit a = unit("Cold_Storage_A", 3.0, 3.5);
        StorageUnit b = unit("Cold_Storage_B", 9.0);
        Forecast forecastA = new Forecast("Cold_Storage_A", 3.25, ComplianceStatus.COMPLIANT, "Moving average", 3);

        RoomTableModel model = new RoomTableModel();
        model.setData(List.of(a, b), List.of(forecastA));

        assertEquals(2, model.getRowCount());
        assertEquals(RoomTableModel.COLUMNS.length, model.getColumnCount());
        assertEquals("Cold_Storage_A", model.getValueAt(0, 0));
        assertEquals("3.50", model.getValueAt(0, 1));
        assertEquals(ComplianceStatus.COMPLIANT, model.getValueAt(0, RoomTableModel.STATUS_COLUMN));
        assertEquals("3.25", model.getValueAt(0, 7));
        assertEquals(ComplianceStatus.COMPLIANT, model.getValueAt(0, RoomTableModel.FORECAST_STATUS_COLUMN));
        // Room B has no forecast yet.
        assertEquals(ComplianceStatus.NON_COMPLIANT, model.getValueAt(1, RoomTableModel.STATUS_COLUMN));
        assertEquals("–", model.getValueAt(1, 7));
        assertNull(model.getValueAt(1, RoomTableModel.FORECAST_STATUS_COLUMN));
    }

    @Test
    void alertTableShowsNewestFirstAndFindsRows() {
        Alert older = alert(9.0);
        Alert newer = alert(4.5);
        AlertTableModel model = new AlertTableModel();
        model.setAlerts(List.of(older, newer));

        assertEquals(2, model.getRowCount());
        assertEquals(newer, model.getAlertAt(0));
        assertEquals("#" + newer.getId(), model.getValueAt(0, 0));
        assertEquals("2024-01-01 00:00", model.getValueAt(0, 2));
        assertEquals("4.50", model.getValueAt(0, 3));
        assertEquals(1, model.findRow(older.getId()));
        assertEquals(-1, model.findRow(-5));
    }

    @Test
    void columnNamesAreReadable() {
        assertEquals("Forecast status", new RoomTableModel().getColumnName(RoomTableModel.FORECAST_STATUS_COLUMN));
        assertEquals("Status", new AlertTableModel().getColumnName(AlertTableModel.STATUS_COLUMN));
    }

    @Test
    void statusColoursFollowTheTrafficLightScheme() {
        assertEquals(StatusCellRenderer.GREEN, StatusCellRenderer.backgroundFor(ComplianceStatus.COMPLIANT));
        assertEquals(StatusCellRenderer.YELLOW, StatusCellRenderer.backgroundFor(ComplianceStatus.WARNING));
        assertEquals(StatusCellRenderer.RED, StatusCellRenderer.backgroundFor(ComplianceStatus.NON_COMPLIANT));
    }

    private static StorageUnit unit(String room, double... temps) {
        StorageUnit unit = new StorageUnit(room);
        ComplianceChecker checker = new ComplianceChecker();
        for (int i = 0; i < temps.length; i++) {
            SensorReading r = new SensorReading(i, room, LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i), temps[i], 80);
            unit.record(r, checker.classify(r));
        }
        return unit;
    }

    private static Alert alert(double temp) {
        SensorReading r = new SensorReading(1, "Cold_Storage_A", LocalDateTime.of(2024, 1, 1, 0, 0), temp, 80);
        return new Alert(r, new ComplianceChecker().classify(r));
    }
}

package edu.northeastern.coldstorage.view.desktop;

import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.Forecast;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;

/**
 * Table data for the desktop dashboard: one row per storage room.
 * Status columns hold {@link ComplianceStatus} values so the renderer can colour them.
 */
public class RoomTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final String[] COLUMNS = {
            "Room", "Current (°C)", "Status", "Mean", "Std dev", "Min", "Max", "Forecast (°C)", "Forecast status"
    };
    static final int STATUS_COLUMN = 2;
    static final int FORECAST_STATUS_COLUMN = 8;

    // Swing table models are never saved to disk, so these fields are transient.
    private transient List<StorageUnit> units = new ArrayList<>();
    private transient Map<String, Forecast> forecasts = new HashMap<>();

    /** Replaces the table contents and tells the table to repaint. */
    public void setData(List<StorageUnit> newUnits, List<Forecast> newForecasts) {
        units = new ArrayList<>(newUnits);
        forecasts = new HashMap<>();
        for (Forecast f : newForecasts) {
            forecasts.put(f.roomId(), f);
        }
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return units.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        StorageUnit unit = units.get(row);
        Forecast forecast = forecasts.get(unit.getRoomId());
        switch (column) {
            case 0:
                return unit.getRoomId();
            case 1:
                return format(unit.getCurrentTemperature());
            case STATUS_COLUMN:
                return unit.getCurrentStatus();
            case 3:
                return format(unit.getMean());
            case 4:
                return format(unit.getStandardDeviation());
            case 5:
                return format(unit.getMinTemperature());
            case 6:
                return format(unit.getMaxTemperature());
            case 7:
                return forecast == null ? "–" : format(forecast.predictedTemperature());
            case FORECAST_STATUS_COLUMN:
                return forecast == null ? null : forecast.predictedStatus();
            default:
                throw new IllegalArgumentException("No column " + column);
        }
    }

    static String format(double value) {
        return Double.isNaN(value) ? "–" : String.format("%.2f", value);
    }
}

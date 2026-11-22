package edu.northeastern.coldstorage.view.desktop;

import edu.northeastern.coldstorage.model.Alert;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Table data for the active-alerts list in the desktop dashboard, newest first. */
public class AlertTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final String[] COLUMNS = {"ID", "Room", "Time", "Temp (°C)", "Status"};
    static final int STATUS_COLUMN = 4;

    // Swing table models are never saved to disk, so these fields are transient.
    private transient List<Alert> alerts = new ArrayList<>();

    /** Replaces the rows; the given list is oldest first, the table shows newest first. */
    public void setAlerts(List<Alert> oldestFirst) {
        List<Alert> newestFirst = new ArrayList<>(oldestFirst);
        Collections.reverse(newestFirst);
        alerts = newestFirst;
        fireTableDataChanged();
    }

    /** Returns the alert shown in a row. */
    public Alert getAlertAt(int row) {
        return alerts.get(row);
    }

    /** Returns the row showing the alert with this id, or -1 if it is not in the table. */
    public int findRow(long alertId) {
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId() == alertId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return alerts.size();
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
        Alert alert = alerts.get(row);
        switch (column) {
            case 0:
                return "#" + alert.getId();
            case 1:
                return alert.getReading().getRoomId();
            case 2:
                return alert.getReading().getTimestamp().toString().replace('T', ' ');
            case 3:
                return RoomTableModel.format(alert.getReading().getTemperature());
            case STATUS_COLUMN:
                return alert.getStatus();
            default:
                throw new IllegalArgumentException("No column " + column);
        }
    }
}

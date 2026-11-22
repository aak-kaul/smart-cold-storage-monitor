package edu.northeastern.coldstorage.view.desktop;

import edu.northeastern.coldstorage.model.ComplianceStatus;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Paints status cells green, yellow or red (the colour scheme from the Phase 1 requirements).
 * The text label is always shown too, so the status is readable without relying on colour.
 */
public class StatusCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    static final Color GREEN = new Color(0xE3, 0xF6, 0xEA);
    static final Color YELLOW = new Color(0xFD, 0xF3, 0xDC);
    static final Color RED = new Color(0xFD, 0xE5, 0xE5);
    private static final Color GREEN_TEXT = new Color(0x1E, 0x8E, 0x4E);
    private static final Color YELLOW_TEXT = new Color(0x97, 0x5A, 0x16);
    private static final Color RED_TEXT = new Color(0xC5, 0x30, 0x30);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.BOLD));
        if (value instanceof ComplianceStatus) {
            ComplianceStatus status = (ComplianceStatus) value;
            setText(status.getLabel());
            if (!isSelected) {
                setBackground(backgroundFor(status));
                setForeground(textFor(status));
            }
        } else {
            setText(value == null ? "–" : value.toString());
            if (!isSelected) {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
        }
        return cell;
    }

    static Color backgroundFor(ComplianceStatus status) {
        switch (status) {
            case COMPLIANT:
                return GREEN;
            case WARNING:
                return YELLOW;
            case NON_COMPLIANT:
            default:
                return RED;
        }
    }

    private static Color textFor(ComplianceStatus status) {
        switch (status) {
            case COMPLIANT:
                return GREEN_TEXT;
            case WARNING:
                return YELLOW_TEXT;
            case NON_COMPLIANT:
            default:
                return RED_TEXT;
        }
    }
}

package edu.northeastern.coldstorage.view.desktop;

import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.command.AcknowledgeAlertCommand;
import edu.northeastern.coldstorage.command.AcknowledgeAllAlertsCommand;
import edu.northeastern.coldstorage.command.ChangePredictorCommand;
import edu.northeastern.coldstorage.command.CommandInvoker;
import edu.northeastern.coldstorage.command.SilenceAlarmCommand;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.ml.PredictionModel;
import edu.northeastern.coldstorage.model.MonitoringReport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * The desktop dashboard (Swing), brought forward from the plan per Phase 1 feedback.
 *
 * <p>MVC: this class is only the <b>view</b>. It reads data from the controller and alert
 * manager (the model side) and turns button clicks into commands for the
 * {@link CommandInvoker}, which plays the controller role for operator actions.
 * It refreshes twice per second and beeps while the alarm is active.
 */
public class DesktopDashboard {

    /** How often the window refreshes, in milliseconds. */
    public static final int REFRESH_MILLIS = 500;
    /** Minimum gap between alarm beeps, in milliseconds. */
    private static final long BEEP_GAP_MILLIS = 2000;
    private static final String OPERATOR = "desktop";
    private static final Color NAVY = new Color(0x13, 0x29, 0x4B);
    private static final Color ALARM_RED = new Color(0xC5, 0x30, 0x30);

    private final MonitorController controller;
    private final AlertManager alertManager;
    private final CommandInvoker invoker;

    private final RoomTableModel roomModel = new RoomTableModel();
    private final AlertTableModel alertModel = new AlertTableModel();
    private final JFrame frame = new JFrame("Smart Cold Storage Monitor");
    private final JLabel summaryLabel = new JLabel("Waiting for sensor readings…");
    private final JLabel contractorLabel = new JLabel(" ");
    private final JLabel alarmLabel = new JLabel(" ");
    private final JLabel statusBar = new JLabel(" ");
    private final JTable alertTable = new JTable(alertModel);
    private final JComboBox<PredictionModel> modelBox = new JComboBox<>(PredictionModel.values());
    private Timer timer;
    private long lastBeep;
    private boolean flash;

    public DesktopDashboard(MonitorController controller, AlertManager alertManager, CommandInvoker invoker) {
        this.controller = Objects.requireNonNull(controller, "controller must not be null");
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager must not be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker must not be null");
    }

    /** Builds and shows the window on the Swing event thread and starts the refresh timer. */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            buildWindow();
            frame.setVisible(true);
            timer = new Timer(REFRESH_MILLIS, e -> refresh());
            timer.start();
            refresh();
        });
    }

    /** Closes the window and stops refreshing. */
    public void close() {
        SwingUtilities.invokeLater(() -> {
            if (timer != null) {
                timer.stop();
            }
            frame.dispose();
        });
    }

    /** Lets the caller decide what happens when the user closes the window (e.g. exit the program). */
    public void onClose(Runnable action) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                action.run();
            }
        });
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(buildHeader(), BorderLayout.NORTH);

        JTable roomTable = new JTable(roomModel);
        roomTable.setRowHeight(28);
        StatusCellRenderer renderer = new StatusCellRenderer();
        roomTable.getColumnModel().getColumn(RoomTableModel.STATUS_COLUMN).setCellRenderer(renderer);
        roomTable.getColumnModel().getColumn(RoomTableModel.FORECAST_STATUS_COLUMN).setCellRenderer(renderer);

        alertTable.setRowHeight(24);
        alertTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alertTable.getColumnModel().getColumn(AlertTableModel.STATUS_COLUMN).setCellRenderer(renderer);

        JPanel roomsPanel = titled(new JScrollPane(roomTable), "Storage units (current state, statistics and forecast)");
        JPanel alertsPanel = titled(new JScrollPane(alertTable), "Active alerts (select one to acknowledge it)");
        alertsPanel.add(buildAlertButtons(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roomsPanel, alertsPanel);
        split.setResizeWeight(0.4);
        frame.add(split, BorderLayout.CENTER);

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        frame.add(statusBar, BorderLayout.SOUTH);
        frame.setPreferredSize(new Dimension(1050, 640));
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        ComplianceChecker rules = controller.getChecker();
        JLabel title = new JLabel(String.format("Smart Cold Storage Monitor   |   safe %.1f–%.1f °C, warning ±%.1f °C",
                rules.getMinSafe(), rules.getMaxSafe(), rules.getWarningMargin()));
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JPanel info = new JPanel(new GridLayout(3, 1));
        info.setOpaque(false);
        for (JLabel label : new JLabel[] {summaryLabel, contractorLabel, alarmLabel}) {
            label.setForeground(Color.WHITE);
            info.add(label);
        }
        alarmLabel.setOpaque(true);
        alarmLabel.setBackground(NAVY);

        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        modelPanel.setOpaque(false);
        JLabel modelLabel = new JLabel("Prediction model:");
        modelLabel.setForeground(Color.WHITE);
        modelPanel.add(modelLabel);
        modelPanel.add(modelBox);
        modelBox.setSelectedItem(matchingModel());
        modelBox.addActionListener(e -> {
            PredictionModel chosen = (PredictionModel) modelBox.getSelectedItem();
            if (chosen != null && chosen != matchingModel()) {
                run("Change prediction model",
                        () -> invoker.execute(OPERATOR, new ChangePredictorCommand(controller, chosen)));
            }
        });

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(title, BorderLayout.NORTH);
        left.add(info, BorderLayout.CENTER);
        header.add(left, BorderLayout.CENTER);
        header.add(modelPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildAlertButtons() {
        JButton acknowledge = new JButton("Acknowledge selected");
        JButton acknowledgeAll = new JButton("Acknowledge all");
        JButton silence = new JButton("Silence alarm");
        acknowledge.addActionListener(e -> {
            int row = alertTable.getSelectedRow();
            if (row < 0) {
                statusBar.setText("Select an alert first.");
                return;
            }
            long id = alertModel.getAlertAt(row).getId();
            run("Acknowledge", () -> invoker.execute(OPERATOR, new AcknowledgeAlertCommand(alertManager, id)));
        });
        acknowledgeAll.addActionListener(e ->
                run("Acknowledge all", () -> invoker.execute(OPERATOR, new AcknowledgeAllAlertsCommand(alertManager))));
        silence.addActionListener(e ->
                run("Silence", () -> invoker.execute(OPERATOR, new SilenceAlarmCommand(alertManager))));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(acknowledge);
        buttons.add(acknowledgeAll);
        buttons.add(silence);
        return buttons;
    }

    /** Runs an operator action, shows its result in the status bar and refreshes. */
    private void run(String what, Supplier<String> action) {
        try {
            statusBar.setText(action.get());
        } catch (RuntimeException ex) {
            statusBar.setText(what + " failed: " + ex.getMessage());
        }
        refresh();
    }

    /** Pulls fresh data from the model side and updates every widget. Runs on the Swing thread. */
    private void refresh() {
        if (controller.hasReadings()) {
            MonitoringReport report = controller.buildReport();
            roomModel.setData(report.getUnits(), report.getForecasts());
            summaryLabel.setText(String.format("Readings: %d   |   Mean: %.2f °C   |   Alerts: %d total, %d active",
                    report.getTotalReadings(), report.getMeanTemperature(),
                    alertManager.getTotalAlerts(), alertManager.getActiveCount()));
            contractorLabel.setText(String.format("Contractor (TMC): %s  (%.1f%% non-compliant, limit %.0f%%)",
                    report.isContractorCompliant() ? "PASS" : "FAIL",
                    report.getPercentNonCompliant(), report.getContractorThresholdPercent()));
        }
        refreshAlerts();
        refreshAlarm();
    }

    /** Updates the alert table while keeping the operator's selected alert selected. */
    private void refreshAlerts() {
        int selectedRow = alertTable.getSelectedRow();
        long selectedId = selectedRow >= 0 ? alertModel.getAlertAt(selectedRow).getId() : -1;
        alertModel.setAlerts(alertManager.getActiveAlerts());
        int row = selectedId >= 0 ? alertModel.findRow(selectedId) : -1;
        if (row >= 0) {
            alertTable.setRowSelectionInterval(row, row);
        }
    }

    private void refreshAlarm() {
        if (alertManager.isAlarmActive()) {
            flash = !flash;
            alarmLabel.setText("  ⚠ ALARM: unacknowledged NON-COMPLIANT readings  ");
            alarmLabel.setBackground(flash ? ALARM_RED : NAVY);
            long now = System.currentTimeMillis();
            if (now - lastBeep >= BEEP_GAP_MILLIS) {
                Toolkit.getDefaultToolkit().beep();
                lastBeep = now;
            }
        } else {
            alarmLabel.setText(alertManager.isSilenced() ? "Alarm silenced" : "No alarm");
            alarmLabel.setBackground(NAVY);
        }
    }

    private PredictionModel matchingModel() {
        String current = controller.getPredictor().name();
        for (PredictionModel model : PredictionModel.values()) {
            if (model.getDisplayName().equals(current)) {
                return model;
            }
        }
        return PredictionModel.MOVING_AVERAGE;
    }

    private static JPanel titled(JScrollPane content, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }
}

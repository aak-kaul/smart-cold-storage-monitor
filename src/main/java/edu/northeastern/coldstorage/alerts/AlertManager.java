package edu.northeastern.coldstorage.alerts;

import edu.northeastern.coldstorage.controller.AlertListener;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps track of alerts for the operator (Functional Requirement 5, alert management).
 *
 * <p>It listens to the controller like any other observer and keeps:
 * <ul>
 *   <li>the <b>active</b> alerts, which are waiting to be acknowledged;</li>
 *   <li>a short history of <b>recent</b> alerts for the dashboards;</li>
 *   <li>the <b>alarm</b> state: the alarm sounds while any active alert is NON-COMPLIANT,
 *       until an operator silences it. A new NON-COMPLIANT alert re-arms a silenced alarm.</li>
 * </ul>
 * Operators change this state only through commands (Command pattern), so every action is audited.
 * All methods are synchronized because sensor threads add alerts while the GUI/web threads read them.
 */
public class AlertManager implements AlertListener {

    /** How many past alerts are kept for display. */
    public static final int RECENT_LIMIT = 100;

    /** Active alerts by id, in the order they were raised. */
    private final Map<Long, Alert> activeAlerts = new LinkedHashMap<>();
    private final Deque<Alert> recentAlerts = new ArrayDeque<>();
    private boolean silenced;
    private int totalAlerts;

    @Override
    public synchronized void onAlert(Alert alert) {
        activeAlerts.put(alert.getId(), alert);
        recentAlerts.addFirst(alert);
        if (recentAlerts.size() > RECENT_LIMIT) {
            recentAlerts.removeLast();
        }
        totalAlerts++;
        if (alert.getStatus() == ComplianceStatus.NON_COMPLIANT) {
            silenced = false; // a new critical alert must be heard again
        }
    }

    /**
     * Acknowledges one alert, removing it from the active list.
     *
     * @param alertId the alert's id
     * @return true if an active alert with that id was found
     */
    public synchronized boolean acknowledge(long alertId) {
        Alert alert = activeAlerts.remove(alertId);
        if (alert == null) {
            return false;
        }
        alert.acknowledge();
        return true;
    }

    /**
     * Acknowledges every active alert.
     *
     * @return how many alerts were acknowledged
     */
    public synchronized int acknowledgeAll() {
        int count = activeAlerts.size();
        for (Alert alert : activeAlerts.values()) {
            alert.acknowledge();
        }
        activeAlerts.clear();
        return count;
    }

    /** Silences the alarm until the next NON-COMPLIANT alert arrives. */
    public synchronized void silenceAlarm() {
        silenced = true;
    }

    /** True if the alarm should sound: an unacknowledged NON-COMPLIANT alert exists and it is not silenced. */
    public synchronized boolean isAlarmActive() {
        return !silenced && hasActiveCriticalAlert();
    }

    public synchronized boolean isSilenced() {
        return silenced;
    }

    /** Returns the active (unacknowledged) alerts, oldest first. */
    public synchronized List<Alert> getActiveAlerts() {
        return Collections.unmodifiableList(new ArrayList<>(activeAlerts.values()));
    }

    /** Returns up to {@code limit} of the most recent alerts, newest first. */
    public synchronized List<Alert> getRecentAlerts(int limit) {
        List<Alert> result = new ArrayList<>();
        for (Alert alert : recentAlerts) {
            if (result.size() == limit) {
                break;
            }
            result.add(alert);
        }
        return Collections.unmodifiableList(result);
    }

    /** Finds an active alert by id. */
    public synchronized Optional<Alert> findActive(long alertId) {
        return Optional.ofNullable(activeAlerts.get(alertId));
    }

    public synchronized int getActiveCount() {
        return activeAlerts.size();
    }

    /** Total alerts received since the start, including acknowledged ones. */
    public synchronized int getTotalAlerts() {
        return totalAlerts;
    }

    private boolean hasActiveCriticalAlert() {
        for (Alert alert : activeAlerts.values()) {
            if (alert.getStatus() == ComplianceStatus.NON_COMPLIANT) {
                return true;
            }
        }
        return false;
    }
}

package edu.northeastern.coldstorage.command;

import edu.northeastern.coldstorage.alerts.AlertManager;
import java.util.Objects;

/** Acknowledges every active alert at once. */
public class AcknowledgeAllAlertsCommand implements OperatorCommand {

    private final AlertManager alertManager;

    public AcknowledgeAllAlertsCommand(AlertManager alertManager) {
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager must not be null");
    }

    @Override
    public String execute() {
        int count = alertManager.acknowledgeAll();
        return "Acknowledged " + count + (count == 1 ? " alert" : " alerts");
    }

    @Override
    public String describe() {
        return "Acknowledge all alerts";
    }
}

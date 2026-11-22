package edu.northeastern.coldstorage.command;

import edu.northeastern.coldstorage.alerts.AlertManager;
import java.util.Objects;

/** Acknowledges one alert by id. */
public class AcknowledgeAlertCommand implements OperatorCommand {

    private final AlertManager alertManager;
    private final long alertId;

    /**
     * Creates the command.
     *
     * @param alertManager where the alert lives
     * @param alertId      the id of the alert to acknowledge
     */
    public AcknowledgeAlertCommand(AlertManager alertManager, long alertId) {
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager must not be null");
        this.alertId = alertId;
    }

    @Override
    public String execute() {
        return alertManager.acknowledge(alertId)
                ? "Acknowledged alert #" + alertId
                : "Alert #" + alertId + " is not active";
    }

    @Override
    public String describe() {
        return "Acknowledge alert #" + alertId;
    }
}

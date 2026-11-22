package edu.northeastern.coldstorage.command;

import edu.northeastern.coldstorage.alerts.AlertManager;
import java.util.Objects;

/** Silences the audio/visual alarm until the next NON-COMPLIANT alert. */
public class SilenceAlarmCommand implements OperatorCommand {

    private final AlertManager alertManager;

    public SilenceAlarmCommand(AlertManager alertManager) {
        this.alertManager = Objects.requireNonNull(alertManager, "alertManager must not be null");
    }

    @Override
    public String execute() {
        alertManager.silenceAlarm();
        return "Alarm silenced";
    }

    @Override
    public String describe() {
        return "Silence alarm";
    }
}

package edu.northeastern.coldstorage.command;

import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.PredictionModel;
import java.util.Objects;

/**
 * Switches the temperature prediction model while the system is running.
 * Because it is a command, the change is recorded in the audit log ("system changes", FR6).
 */
public class ChangePredictorCommand implements OperatorCommand {

    private final MonitorController controller;
    private final PredictionModel model;

    public ChangePredictorCommand(MonitorController controller, PredictionModel model) {
        this.controller = Objects.requireNonNull(controller, "controller must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
    }

    @Override
    public String execute() {
        controller.setPredictor(model.create());
        return "Prediction model set to " + model.getDisplayName();
    }

    @Override
    public String describe() {
        return "Change prediction model";
    }
}

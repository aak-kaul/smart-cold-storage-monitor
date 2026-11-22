package edu.northeastern.coldstorage.command;

import edu.northeastern.coldstorage.ml.PredictionService;
import edu.northeastern.coldstorage.ml.TemperaturePredictor;
import java.util.Objects;

/**
 * Switches the temperature prediction model (Strategy) that {@link PredictionService} uses.
 *
 * <p>Running the switch as a command means it goes through the {@link CommandInvoker}, so the
 * change is recorded in the audit log like every other operator action, whether it came
 * from the desktop GUI or the web dashboard.
 */
public class ChangePredictorCommand implements OperatorCommand {

    private final PredictionService predictionService;
    private final TemperaturePredictor newPredictor;

    /**
     * Creates the command.
     *
     * @param predictionService the service whose model should change
     * @param newPredictor      the model to switch to
     */
    public ChangePredictorCommand(PredictionService predictionService, TemperaturePredictor newPredictor) {
        this.predictionService = Objects.requireNonNull(predictionService, "predictionService must not be null");
        this.newPredictor = Objects.requireNonNull(newPredictor, "newPredictor must not be null");
    }

    @Override
    public String execute() {
        String previous = predictionService.getPredictor().getName();
        predictionService.setPredictor(newPredictor);
        return "Prediction model changed from " + previous + " to " + newPredictor.getName();
    }

    @Override
    public String describe() {
        return "Change prediction model";
    }
}

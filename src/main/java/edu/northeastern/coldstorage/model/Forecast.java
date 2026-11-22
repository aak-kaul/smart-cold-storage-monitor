package edu.northeastern.coldstorage.model;

import java.util.Objects;

/**
 * A predicted future temperature for one storage room.
 *
 * @param roomId               the room the forecast is for
 * @param predictedTemperature the predicted temperature in °C
 * @param predictedStatus      how that temperature would be classified
 * @param modelName            which prediction model produced it
 * @param stepsAhead           how many readings ahead it looks
 */
public record Forecast(String roomId, double predictedTemperature, ComplianceStatus predictedStatus,
                       String modelName, int stepsAhead) {

    public Forecast {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(predictedStatus, "predictedStatus must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (stepsAhead < 1) {
            throw new IllegalArgumentException("stepsAhead must be at least 1");
        }
        if (!Double.isFinite(predictedTemperature)) {
            throw new IllegalArgumentException("predictedTemperature must be a finite number");
        }
    }

    /** True if the room is predicted to leave the safe range (spoilage risk). */
    public boolean isRiskPredicted() {
        return predictedStatus != ComplianceStatus.COMPLIANT;
    }
}

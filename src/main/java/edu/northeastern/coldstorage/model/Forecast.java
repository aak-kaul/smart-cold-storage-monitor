package edu.northeastern.coldstorage.model;

import java.util.Objects;

/**
 * One immutable temperature forecast for a storage room: which model made it, how far
 * ahead it looks, the predicted temperature, and the compliance status that temperature
 * would have. Views (console, desktop GUI, web dashboard) display forecasts without
 * needing to know which prediction model produced them.
 */
public final class Forecast {

    private final String roomId;
    private final String modelName;
    private final int stepsAhead;
    private final double predictedTemperature;
    private final ComplianceStatus predictedStatus;

    /**
     * Creates a forecast.
     *
     * @param roomId               the room the forecast is for
     * @param modelName            the prediction model that made it, e.g. "Linear regression"
     * @param stepsAhead           how many readings ahead it predicts; at least 1
     * @param predictedTemperature the predicted temperature in degrees Celsius
     * @param predictedStatus      the compliance status of the predicted temperature
     * @throws IllegalArgumentException if stepsAhead is less than 1 or the temperature is not finite
     */
    public Forecast(String roomId, String modelName, int stepsAhead,
                    double predictedTemperature, ComplianceStatus predictedStatus) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.predictedStatus = Objects.requireNonNull(predictedStatus, "predictedStatus must not be null");
        if (stepsAhead < 1) {
            throw new IllegalArgumentException("stepsAhead must be at least 1");
        }
        if (!Double.isFinite(predictedTemperature)) {
            throw new IllegalArgumentException("predictedTemperature must be a finite number");
        }
        this.stepsAhead = stepsAhead;
        this.predictedTemperature = predictedTemperature;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getModelName() {
        return modelName;
    }

    public int getStepsAhead() {
        return stepsAhead;
    }

    public double getPredictedTemperature() {
        return predictedTemperature;
    }

    public ComplianceStatus getPredictedStatus() {
        return predictedStatus;
    }

    /** Returns true if the predicted temperature is outside the safe range (WARNING or worse). */
    public boolean isUnsafe() {
        return predictedStatus != ComplianceStatus.COMPLIANT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Forecast)) {
            return false;
        }
        Forecast other = (Forecast) o;
        return stepsAhead == other.stepsAhead
                && Double.compare(predictedTemperature, other.predictedTemperature) == 0
                && roomId.equals(other.roomId)
                && modelName.equals(other.modelName)
                && predictedStatus == other.predictedStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, modelName, stepsAhead, predictedTemperature, predictedStatus);
    }

    @Override
    public String toString() {
        return String.format("%s +%d: %.2f C (%s, %s)",
                roomId, stepsAhead, predictedTemperature, predictedStatus, modelName);
    }
}

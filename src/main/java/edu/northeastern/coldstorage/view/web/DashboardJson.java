package edu.northeastern.coldstorage.view.web;

import edu.northeastern.coldstorage.alerts.AlertManager;
import edu.northeastern.coldstorage.controller.MonitorController;
import edu.northeastern.coldstorage.ml.ComplianceChecker;
import edu.northeastern.coldstorage.model.Alert;
import edu.northeastern.coldstorage.model.ComplianceStatus;
import edu.northeastern.coldstorage.model.Forecast;
import edu.northeastern.coldstorage.model.MonitoringReport;
import edu.northeastern.coldstorage.model.StorageUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the system's current state into the JSON the web dashboard reads.
 * Kept separate from the HTTP server so it can be unit-tested without a network.
 */
public final class DashboardJson {

    /** How many recent alerts the dashboard shows. */
    static final int RECENT_ALERTS = 30;

    private DashboardJson() {
    }

    /** Builds the JSON for {@code GET /api/status}: totals, limits, rooms and forecasts. */
    public static String status(MonitorController controller, AlertManager alerts) {
        ComplianceChecker checker = controller.getChecker();
        JsonWriter json = new JsonWriter().beginObject();
        json.name("limits").beginObject()
                .name("minSafe").value(checker.getMinSafe())
                .name("maxSafe").value(checker.getMaxSafe())
                .name("warningMargin").value(checker.getWarningMargin())
                .endObject();
        json.name("predictor").value(controller.getPredictor().name());
        json.name("alarmActive").value(alerts.isAlarmActive());
        json.name("alarmSilenced").value(alerts.isSilenced());
        json.name("activeAlerts").value(alerts.getActiveCount());
        json.name("totalAlerts").value(alerts.getTotalAlerts());

        if (!controller.hasReadings()) {
            json.name("readings").value(0);
            json.name("rooms").beginArray().endArray();
            return json.endObject().toString();
        }

        MonitoringReport report = controller.buildReport();
        json.name("readings").value(report.getTotalReadings());
        json.name("meanTemperature").value(report.getMeanTemperature());
        json.name("variance").value(report.getVariance());
        json.name("counts").beginObject();
        for (ComplianceStatus s : ComplianceStatus.values()) {
            json.name(s.name()).value(report.getCount(s));
        }
        json.endObject();
        json.name("percentNonCompliant").value(report.getPercentNonCompliant());
        json.name("contractorThreshold").value(report.getContractorThresholdPercent());
        json.name("contractorCompliant").value(report.isContractorCompliant());

        Map<String, Forecast> forecasts = new HashMap<>();
        for (Forecast f : report.getForecasts()) {
            forecasts.put(f.roomId(), f);
        }
        json.name("rooms").beginArray();
        for (StorageUnit unit : report.getUnits()) {
            writeRoom(json, unit, forecasts.get(unit.getRoomId()));
        }
        json.endArray();
        return json.endObject().toString();
    }

    /** Builds the JSON for {@code GET /api/alerts}: active alerts and recent history. */
    public static String alerts(AlertManager alerts) {
        JsonWriter json = new JsonWriter().beginObject();
        json.name("active");
        writeAlerts(json, alerts.getActiveAlerts());
        json.name("recent");
        writeAlerts(json, alerts.getRecentAlerts(RECENT_ALERTS));
        return json.endObject().toString();
    }

    /** Builds a simple {@code {"message": "..."}} response. */
    public static String message(String message) {
        return new JsonWriter().beginObject().name("message").value(message).endObject().toString();
    }

    private static void writeRoom(JsonWriter json, StorageUnit unit, Forecast forecast) {
        ComplianceStatus status = unit.getCurrentStatus();
        json.beginObject()
                .name("roomId").value(unit.getRoomId())
                .name("readings").value(unit.getReadingCount())
                .name("current").value(unit.getCurrentTemperature())
                .name("status").value(status.name())
                .name("statusLabel").value(status.getLabel())
                .name("mean").value(unit.getMean())
                .name("variance").value(unit.getVariance())
                .name("stdDev").value(unit.getStandardDeviation())
                .name("min").value(unit.getMinTemperature())
                .name("max").value(unit.getMaxTemperature())
                .name("green").value(unit.getCount(ComplianceStatus.COMPLIANT))
                .name("yellow").value(unit.getCount(ComplianceStatus.WARNING))
                .name("red").value(unit.getCount(ComplianceStatus.NON_COMPLIANT));
        json.name("history").beginArray();
        for (double t : unit.getRecentTemperatures()) {
            json.value(t);
        }
        json.endArray();
        json.name("forecast");
        if (forecast == null) {
            json.nullValue();
        } else {
            json.beginObject()
                    .name("temperature").value(forecast.predictedTemperature())
                    .name("status").value(forecast.predictedStatus().name())
                    .name("statusLabel").value(forecast.predictedStatus().getLabel())
                    .name("risk").value(forecast.isRiskPredicted())
                    .name("stepsAhead").value(forecast.stepsAhead())
                    .name("model").value(forecast.modelName())
                    .endObject();
        }
        json.endObject();
    }

    private static void writeAlerts(JsonWriter json, List<Alert> alerts) {
        json.beginArray();
        for (Alert alert : alerts) {
            json.beginObject()
                    .name("id").value(alert.getId())
                    .name("roomId").value(alert.getReading().getRoomId())
                    .name("time").value(alert.getReading().getTimestamp().toString())
                    .name("temperature").value(alert.getReading().getTemperature())
                    .name("status").value(alert.getStatus().name())
                    .name("statusLabel").value(alert.getStatus().getLabel())
                    .name("acknowledged").value(alert.isAcknowledged())
                    .endObject();
        }
        json.endArray();
    }
}

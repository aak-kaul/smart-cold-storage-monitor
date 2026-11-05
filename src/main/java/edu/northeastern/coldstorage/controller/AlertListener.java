package edu.northeastern.coldstorage.controller;

import edu.northeastern.coldstorage.model.Alert;

/**
 * Observer interface: anything that wants to be told about alerts implements this
 * and registers with {@link MonitorController#addListener(AlertListener)}.
 */
public interface AlertListener {

    /**
     * Called each time a WARNING or NON_COMPLIANT reading is detected.
     *
     * @param alert the new alert
     */
    void onAlert(Alert alert);
}
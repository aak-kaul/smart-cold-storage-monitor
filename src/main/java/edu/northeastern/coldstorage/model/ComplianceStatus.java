package edu.northeastern.coldstorage.model;

/**
 * Compliance classification for a single temperature reading.
 *
 * <p>Each status carries the colour used by the views
 * (green = compliant, yellow = warning, red = non-compliant).
 */
public enum ComplianceStatus {
    COMPLIANT("COMPLIANT", "GREEN"),
    WARNING("WARNING", "YELLOW"),
    NON_COMPLIANT("NON-COMPLIANT", "RED");

    private final String label;
    private final String colour;

    ComplianceStatus(String label, String colour) {
        this.label = label;
        this.colour = colour;
    }

    /** Returns the human-readable label, e.g. "NON-COMPLIANT". */
    public String getLabel() {
        return label;
    }

    /** Returns the display colour for this status. */
    public String getColour() {
        return colour;
    }

    @Override
    public String toString() {
        return label;
    }
}
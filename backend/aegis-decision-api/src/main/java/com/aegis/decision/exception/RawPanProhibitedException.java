package com.aegis.decision.exception;

/**
 * Thrown when raw Primary Account Number (PAN) is detected in free-text fields.
 */
public class RawPanProhibitedException extends RuntimeException {
    private final String fieldName;

    public RawPanProhibitedException(String fieldName) {
        super("Primary Account Numbers (PANs) are strictly prohibited in transaction payloads.");
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}

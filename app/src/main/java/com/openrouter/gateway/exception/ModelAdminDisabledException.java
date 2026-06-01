package com.openrouter.gateway.exception;

/**
 * Thrown when a user attempts to toggle a model that has been disabled by an admin.
 * Users can only filter down from the admin-enabled allowlist; they cannot re-enable
 * admin-disabled models. Maps to HTTP 400 via {@link GlobalExceptionHandler}.
 */
public class ModelAdminDisabledException extends RuntimeException {

    private final String modelId;

    public ModelAdminDisabledException(String modelId) {
        super("This model is admin-disabled and cannot be toggled by users");
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }
}

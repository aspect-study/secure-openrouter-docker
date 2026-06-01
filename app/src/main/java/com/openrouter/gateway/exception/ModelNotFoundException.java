package com.openrouter.gateway.exception;

/**
 * Thrown when a model_config row cannot be found by its integer primary key.
 * Maps to HTTP 404 via {@link GlobalExceptionHandler}.
 */
public class ModelNotFoundException extends RuntimeException {

    private final Long modelConfigId;

    public ModelNotFoundException(Long modelConfigId) {
        super("Model not found: id=" + modelConfigId);
        this.modelConfigId = modelConfigId;
    }

    public Long getModelConfigId() {
        return modelConfigId;
    }
}

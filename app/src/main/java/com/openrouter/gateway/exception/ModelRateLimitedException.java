package com.openrouter.gateway.exception;

public class ModelRateLimitedException extends RuntimeException {

    private final String modelId;

    public ModelRateLimitedException(String modelId) {
        super("Model '" + modelId + "' is temporarily rate-limited upstream.");
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }
}

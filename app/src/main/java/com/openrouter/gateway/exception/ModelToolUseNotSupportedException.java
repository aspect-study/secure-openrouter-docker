package com.openrouter.gateway.exception;

public class ModelToolUseNotSupportedException extends RuntimeException {

    private final String modelId;

    public ModelToolUseNotSupportedException(String modelId) {
        super("Model '" + modelId + "' does not support tool use via OpenRouter. " +
              "Choose a model that supports function calling (e.g. meta-llama/llama-3.3-70b-instruct:free).");
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }
}

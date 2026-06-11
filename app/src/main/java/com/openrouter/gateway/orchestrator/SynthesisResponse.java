package com.openrouter.gateway.orchestrator;

public record SynthesisResponse(
        String content,
        String modelId,
        String modelName
) {}

package com.openrouter.gateway.orchestrator;

public record OrchestratorResult(
        String modelId,
        String name,
        String content,
        long latencyMs,
        String status
) {}

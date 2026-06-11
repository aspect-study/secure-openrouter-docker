package com.openrouter.gateway.orchestrator;

import jakarta.validation.constraints.NotBlank;

public record OrchestrateRequest(@NotBlank String prompt) {}

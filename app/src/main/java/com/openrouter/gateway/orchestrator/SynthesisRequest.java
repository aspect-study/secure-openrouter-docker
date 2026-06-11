package com.openrouter.gateway.orchestrator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SynthesisRequest(
        @NotBlank String prompt,
        @NotEmpty List<OrchestratorResult> responses
) {}

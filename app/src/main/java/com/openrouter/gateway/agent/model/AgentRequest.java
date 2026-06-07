package com.openrouter.gateway.agent.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AgentRequest(
        @NotBlank String question,
        String model,
        List<HistoryMessage> history
) {
    public record HistoryMessage(String role, String content) {}
}

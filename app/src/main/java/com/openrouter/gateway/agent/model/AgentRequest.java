package com.openrouter.gateway.agent.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(@NotBlank String question, String model) {}

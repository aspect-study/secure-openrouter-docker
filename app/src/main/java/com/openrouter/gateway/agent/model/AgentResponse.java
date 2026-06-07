package com.openrouter.gateway.agent.model;

import java.util.List;

public record AgentResponse(String reply, List<ToolStep> toolSteps) {}

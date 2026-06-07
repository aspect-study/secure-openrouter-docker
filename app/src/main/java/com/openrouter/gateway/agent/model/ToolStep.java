package com.openrouter.gateway.agent.model;

import java.util.Map;

/** One tool invocation, surfaced to the UI so the ReAct loop is observable. */
public record ToolStep(String toolName, Map<String, Object> input, Map<String, Object> result) {}

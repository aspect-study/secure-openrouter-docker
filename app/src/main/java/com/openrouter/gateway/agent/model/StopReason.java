package com.openrouter.gateway.agent.model;

/**
 * Claude API stop_reason values, mapped from OpenAI-compatible finish_reason
 * by {@link com.openrouter.gateway.agent.adapter.OpenRouterAdapter}.
 */
public enum StopReason {
    END_TURN,
    TOOL_USE,
    MAX_TOKENS,
    UNKNOWN;

    public static StopReason fromOpenAiFinishReason(String finishReason) {
        if (finishReason == null) return UNKNOWN;
        return switch (finishReason) {
            case "stop" -> END_TURN;
            case "tool_calls" -> TOOL_USE;
            case "length" -> MAX_TOKENS;
            default -> UNKNOWN;
        };
    }
}

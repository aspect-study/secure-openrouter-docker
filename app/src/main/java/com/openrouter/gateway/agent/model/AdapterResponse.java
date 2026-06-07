package com.openrouter.gateway.agent.model;

import java.util.List;

/**
 * Claude API assistant turn — stop_reason plus the content blocks the model produced.
 * Returned by {@link com.openrouter.gateway.agent.adapter.OpenRouterAdapter#send}.
 */
public record AdapterResponse(StopReason stopReason, List<ContentBlock> content) {

    /** Concatenates all text blocks with newlines; empty string if there are none. */
    public String text() {
        return content.stream()
                .filter(ContentBlock.TextBlock.class::isInstance)
                .map(ContentBlock.TextBlock.class::cast)
                .map(ContentBlock.TextBlock::text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    public List<ContentBlock.ToolUseBlock> toolUseBlocks() {
        return content.stream()
                .filter(ContentBlock.ToolUseBlock.class::isInstance)
                .map(ContentBlock.ToolUseBlock.class::cast)
                .toList();
    }
}

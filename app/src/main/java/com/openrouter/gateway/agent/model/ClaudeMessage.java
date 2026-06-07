package com.openrouter.gateway.agent.model;

import java.util.List;

/**
 * Claude API message — role + ordered content blocks.
 * Tool results are sent back as a "user" role message per the Claude API
 * convention (see PRD-005 mapping table).
 */
public record ClaudeMessage(String role, List<ContentBlock> content) {

    public static ClaudeMessage user(String text) {
        return new ClaudeMessage("user", List.of(new ContentBlock.TextBlock(text)));
    }

    public static ClaudeMessage assistant(List<ContentBlock> content) {
        return new ClaudeMessage("assistant", content);
    }

    public static ClaudeMessage toolResults(List<ContentBlock.ToolResultBlock> results) {
        return new ClaudeMessage("user", List.<ContentBlock>copyOf(results));
    }
}

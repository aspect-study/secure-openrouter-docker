package com.openrouter.gateway.agent.model;

import java.util.Map;

/**
 * Claude API content block — a sealed union of the three block types this
 * agent ever produces or consumes.
 */
public sealed interface ContentBlock {

    record TextBlock(String text) implements ContentBlock {}

    record ToolUseBlock(String id, String name, Map<String, Object> input) implements ContentBlock {}

    record ToolResultBlock(String toolUseId, String content) implements ContentBlock {}
}

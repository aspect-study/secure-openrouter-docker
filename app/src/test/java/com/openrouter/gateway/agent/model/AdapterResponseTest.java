package com.openrouter.gateway.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdapterResponseTest {

    @Test
    void textJoinsAllTextBlocksAndIgnoresOtherBlockTypes() {
        AdapterResponse response = new AdapterResponse(StopReason.END_TURN, List.of(
                new ContentBlock.TextBlock("Line one."),
                new ContentBlock.ToolUseBlock("toolu_1", "get_model_status", Map.of()),
                new ContentBlock.TextBlock("Line two.")
        ));

        assertThat(response.text()).isEqualTo("Line one.\nLine two.");
    }

    @Test
    void textReturnsEmptyStringWhenNoTextBlocksPresent() {
        AdapterResponse response = new AdapterResponse(StopReason.TOOL_USE, List.of(
                new ContentBlock.ToolUseBlock("toolu_1", "get_model_status", Map.of())
        ));

        assertThat(response.text()).isEmpty();
    }

    @Test
    void toolUseBlocksReturnsOnlyToolUseBlocksInOrder() {
        ContentBlock.ToolUseBlock first = new ContentBlock.ToolUseBlock("toolu_1", "get_model_status", Map.of());
        ContentBlock.ToolUseBlock second = new ContentBlock.ToolUseBlock("toolu_2", "get_gateway_stats", Map.of());

        AdapterResponse response = new AdapterResponse(StopReason.TOOL_USE, List.of(
                new ContentBlock.TextBlock("Let me check that."),
                first,
                second
        ));

        assertThat(response.toolUseBlocks()).containsExactly(first, second);
    }
}

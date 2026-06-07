package com.openrouter.gateway.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeMessageTest {

    @Test
    void userFactoryWrapsTextInSingleTextBlock() {
        ClaudeMessage message = ClaudeMessage.user("What models are enabled?");

        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content()).hasSize(1);
        assertThat(message.content().get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(((ContentBlock.TextBlock) message.content().get(0)).text())
                .isEqualTo("What models are enabled?");
    }

    @Test
    void assistantFactoryPreservesGivenContentBlocks() {
        ContentBlock.ToolUseBlock toolUse = new ContentBlock.ToolUseBlock(
                "toolu_1", "get_model_status", Map.of("model_id", "x"));

        ClaudeMessage message = ClaudeMessage.assistant(List.of(toolUse));

        assertThat(message.role()).isEqualTo("assistant");
        assertThat(message.content()).containsExactly(toolUse);
    }

    @Test
    void toolResultsFactoryWrapsResultsInUserRoleMessage() {
        ContentBlock.ToolResultBlock result = new ContentBlock.ToolResultBlock("toolu_1", "{\"enabled\":true}");

        ClaudeMessage message = ClaudeMessage.toolResults(List.of(result));

        assertThat(message.role()).isEqualTo("user");
        assertThat(message.content()).containsExactly(result);
    }
}

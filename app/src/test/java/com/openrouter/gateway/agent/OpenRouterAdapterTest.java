package com.openrouter.gateway.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.agent.adapter.OpenRouterAdapter;
import com.openrouter.gateway.agent.model.ClaudeMessage;
import com.openrouter.gateway.agent.model.ContentBlock;
import com.openrouter.gateway.agent.tool.GatewayTool;
import com.openrouter.gateway.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenRouterAdapter}'s request-building logic.
 * HTTP calls are not exercised — only the translation from Claude format to OpenAI format.
 */
class OpenRouterAdapterTest {

    private OpenRouterAdapter adapter;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getOpenrouter().setProxyUrl("http://localhost:8081");
        adapter = new OpenRouterAdapter(props, new ObjectMapper());
    }

    @Test
    void userTextMessage_convertsToOpenAiFormat() {
        List<ClaudeMessage> messages = List.of(ClaudeMessage.user("What is the status of model X?"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = adapter.buildRequestBody(messages, List.of(), "test-model");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> openAiMessages = (List<Map<String, Object>>) body.get("messages");
        assertThat(openAiMessages).hasSize(1);
        assertThat(openAiMessages.get(0)).containsEntry("role", "user")
                .containsEntry("content", "What is the status of model X?");
    }

    @Test
    void assistantToolUseMessage_convertsToToolCallsFormat() {
        List<ContentBlock> blocks = List.of(
                new ContentBlock.ToolUseBlock("call-1", "get_model_status", Map.of("model_id", "llama:free"))
        );
        List<ClaudeMessage> messages = List.of(ClaudeMessage.assistant(blocks));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = adapter.buildRequestBody(messages, List.of(), "test-model");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> openAiMessages = (List<Map<String, Object>>) body.get("messages");
        assertThat(openAiMessages).hasSize(1);

        Map<String, Object> msg = openAiMessages.get(0);
        assertThat(msg).containsEntry("role", "assistant").containsEntry("content", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0)).containsEntry("id", "call-1").containsEntry("type", "function");

        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) toolCalls.get(0).get("function");
        assertThat(fn).containsEntry("name", "get_model_status");
        assertThat(fn.get("arguments").toString()).contains("llama:free");
    }

    @Test
    void toolResultMessage_convertsToToolRoleFormat() {
        List<ContentBlock.ToolResultBlock> results = List.of(
                new ContentBlock.ToolResultBlock("call-1", "{\"enabled\":true}")
        );
        List<ClaudeMessage> messages = List.of(ClaudeMessage.toolResults(results));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = adapter.buildRequestBody(messages, List.of(), "test-model");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> openAiMessages = (List<Map<String, Object>>) body.get("messages");
        assertThat(openAiMessages).hasSize(1);
        assertThat(openAiMessages.get(0))
                .containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call-1")
                .containsEntry("content", "{\"enabled\":true}");
    }

    @Test
    void toolsList_convertsToFunctionDefinitions() {
        GatewayTool stubTool = new GatewayTool() {
            @Override public String name() { return "my_tool"; }
            @Override public String description() { return "Does something useful"; }
            @Override public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public Map<String, Object> execute(Map<String, Object> input) { return Map.of(); }
        };

        @SuppressWarnings("unchecked")
        Map<String, Object> body = adapter.buildRequestBody(List.of(), List.of(stubTool), "test-model");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).containsEntry("type", "function");

        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) tools.get(0).get("function");
        assertThat(fn)
                .containsEntry("name", "my_tool")
                .containsEntry("description", "Does something useful");
    }

    @Test
    void requestBody_containsRequiredTopLevelFields() {
        Map<String, Object> body = adapter.buildRequestBody(List.of(), List.of(), "nvidia/nemotron:free");

        assertThat(body)
                .containsEntry("model", "nvidia/nemotron:free")
                .containsEntry("tool_choice", "auto")
                .containsEntry("stream", false);
    }
}

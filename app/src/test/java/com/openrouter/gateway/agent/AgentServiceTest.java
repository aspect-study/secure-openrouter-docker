package com.openrouter.gateway.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.agent.adapter.OpenRouterAdapter;
import com.openrouter.gateway.agent.model.*;
import com.openrouter.gateway.agent.tool.GatewayTool;
import com.openrouter.gateway.apikey.OpenRouterKeyService;
import com.openrouter.gateway.config.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private OpenRouterAdapter adapter;

    @Mock
    private OpenRouterKeyService keyService;

    @Mock
    private ModelConfigService modelConfigService;

    private GatewayTool stubTool;
    private AgentService service;

    @BeforeEach
    void setUp() {
        stubTool = new GatewayTool() {
            @Override public String name() { return "get_gateway_stats"; }
            @Override public String description() { return "Returns stats"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override public Map<String, Object> execute(Map<String, Object> input) {
                return Map.of("totalRequests", 42L);
            }
        };
        service = new AgentService(adapter, List.of(stubTool), keyService, modelConfigService, new ObjectMapper());
    }

    @Test
    void singleEndTurnResponse_returnsReplyDirectlyWithNoToolSteps() {
        when(keyService.getKeyForUser("admin@test.com")).thenReturn("sk-or-test");
        AdapterResponse endTurnResponse = new AdapterResponse(
                StopReason.END_TURN,
                List.of(new ContentBlock.TextBlock("The gateway processed 42 requests today."))
        );
        when(adapter.call(anyList(), anyList(), anyString(), anyString())).thenReturn(endTurnResponse);

        AgentResponse result = service.run(new AgentRequest("How many requests today?", null, null), "admin@test.com", ignored -> {});

        assertThat(result.reply()).isEqualTo("The gateway processed 42 requests today.");
        assertThat(result.toolSteps()).isEmpty();
        verify(adapter, times(1)).call(anyList(), anyList(), anyString(), eq("sk-or-test"));
    }

    @Test
    void toolCallRoundTrip_callsToolAndReturnsCorrectReplyAndToolSteps() {
        when(keyService.getKeyForUser("admin@test.com")).thenReturn("sk-or-test");

        AdapterResponse toolUseResponse = new AdapterResponse(
                StopReason.TOOL_USE,
                List.of(new ContentBlock.ToolUseBlock("call-1", "get_gateway_stats", Map.of()))
        );
        AdapterResponse finalResponse = new AdapterResponse(
                StopReason.END_TURN,
                List.of(new ContentBlock.TextBlock("There were 42 requests today."))
        );
        when(adapter.call(anyList(), anyList(), anyString(), anyString()))
                .thenReturn(toolUseResponse)
                .thenReturn(finalResponse);

        AgentResponse result = service.run(new AgentRequest("Check stats", null, null), "admin@test.com", ignored -> {});

        assertThat(result.reply()).isEqualTo("There were 42 requests today.");
        assertThat(result.toolSteps()).hasSize(1);
        assertThat(result.toolSteps().get(0).toolName()).isEqualTo("get_gateway_stats");
        assertThat(result.toolSteps().get(0).result()).containsEntry("totalRequests", 42L);
        verify(adapter, times(2)).call(anyList(), anyList(), anyString(), anyString());
    }

    @Test
    void maxTurnsExceeded_loopStopsAfterTenIterations() {
        when(keyService.getKeyForUser("admin@test.com")).thenReturn("sk-or-test");

        AdapterResponse toolUseResponse = new AdapterResponse(
                StopReason.TOOL_USE,
                List.of(new ContentBlock.ToolUseBlock("call-x", "get_gateway_stats", Map.of()))
        );
        when(adapter.call(anyList(), anyList(), anyString(), anyString())).thenReturn(toolUseResponse);

        AgentResponse result = service.run(new AgentRequest("Loop forever", null, null), "admin@test.com", ignored -> {});

        verify(adapter, times(10)).call(anyList(), anyList(), anyString(), anyString());
        assertThat(result.toolSteps().size()).isLessThanOrEqualTo(10);
    }

    @Test
    void unknownToolName_returnsErrorResultBlock() {
        when(keyService.getKeyForUser("admin@test.com")).thenReturn("sk-or-test");

        AdapterResponse toolUseResponse = new AdapterResponse(
                StopReason.TOOL_USE,
                List.of(new ContentBlock.ToolUseBlock("call-1", "nonexistent_tool", Map.of()))
        );
        AdapterResponse finalResponse = new AdapterResponse(
                StopReason.END_TURN,
                List.of(new ContentBlock.TextBlock("I could not call the tool."))
        );
        when(adapter.call(anyList(), anyList(), anyString(), anyString()))
                .thenReturn(toolUseResponse)
                .thenReturn(finalResponse);

        AgentResponse result = service.run(new AgentRequest("Use unknown tool", null, null), "admin@test.com", ignored -> {});

        assertThat(result.toolSteps()).hasSize(1);
        assertThat(result.toolSteps().get(0).result()).containsEntry("error", "unknown tool: nonexistent_tool");
    }

    @Test
    void nullModel_usesDefaultModel() {
        when(keyService.getKeyForUser("admin@test.com")).thenReturn("sk-or-test");
        AdapterResponse endTurnResponse = new AdapterResponse(
                StopReason.END_TURN,
                List.of(new ContentBlock.TextBlock("Done."))
        );
        when(adapter.call(anyList(), anyList(), anyString(), anyString())).thenReturn(endTurnResponse);

        service.run(new AgentRequest("Hello", null, null), "admin@test.com", ignored -> {});

        verify(adapter).call(anyList(), anyList(), eq("meta-llama/llama-3.3-70b-instruct:free"), anyString());
    }
}

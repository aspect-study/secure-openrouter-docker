package com.openrouter.gateway.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.agent.adapter.OpenRouterAdapter;
import com.openrouter.gateway.agent.model.*;
import com.openrouter.gateway.agent.tool.GatewayTool;
import com.openrouter.gateway.apikey.OpenRouterKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ReAct-loop agent. Speaks Claude API vocabulary exclusively.
 * All OpenAI-format concerns are delegated to {@link OpenRouterAdapter}.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_TURNS = 10;
    private static final String DEFAULT_MODEL = "nvidia/nemotron-nano-9b-v2:free";

    private final OpenRouterAdapter adapter;
    private final List<GatewayTool> tools;
    private final OpenRouterKeyService keyService;
    private final ObjectMapper objectMapper;

    public AgentService(OpenRouterAdapter adapter,
                        List<GatewayTool> tools,
                        OpenRouterKeyService keyService,
                        ObjectMapper objectMapper) {
        this.adapter = adapter;
        this.tools = tools;
        this.keyService = keyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs the ReAct loop for the given question and returns the agent's reply
     * plus a trace of all tool invocations made during the loop.
     *
     * @param request   validated agent request (question + optional model)
     * @param userEmail authenticated user's email — used to retrieve their BYOK key
     * @throws com.openrouter.gateway.exception.KeyNotConfiguredException if no API key is saved
     */
    public AgentResponse run(AgentRequest request, String userEmail) {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL
                : request.model();

        String apiKey = keyService.getKeyForUser(userEmail);

        Map<String, GatewayTool> toolIndex = tools.stream()
                .collect(Collectors.toMap(GatewayTool::name, Function.identity()));

        List<ClaudeMessage> messages = new ArrayList<>();
        messages.add(ClaudeMessage.user(request.question()));

        List<ToolStep> toolSteps = new ArrayList<>();
        String lastText = "";

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            log.debug("Agent turn {}/{}: model={}", turn + 1, MAX_TURNS, model);

            AdapterResponse response = adapter.call(messages, tools, model, apiKey);

            messages.add(ClaudeMessage.assistant(response.content()));

            lastText = response.text();

            List<ContentBlock.ToolUseBlock> toolUseBlocks = response.toolUseBlocks();

            if (response.stopReason() == StopReason.END_TURN || toolUseBlocks.isEmpty()) {
                log.debug("Agent loop complete: stopReason={}", response.stopReason());
                break;
            }

            if (response.stopReason() == StopReason.MAX_TOKENS) {
                log.warn("Agent stopped: MAX_TOKENS reached at turn {}", turn + 1);
                break;
            }

            List<ContentBlock.ToolResultBlock> resultBlocks = new ArrayList<>();
            for (ContentBlock.ToolUseBlock tub : toolUseBlocks) {
                GatewayTool tool = toolIndex.get(tub.name());
                Map<String, Object> result;
                if (tool == null) {
                    log.warn("Agent called unknown tool: {}", tub.name());
                    result = Map.of("error", "unknown tool: " + tub.name());
                } else {
                    result = tool.execute(tub.input());
                    log.debug("Tool '{}' executed, result keys: {}", tub.name(), result.keySet());
                }

                toolSteps.add(new ToolStep(tub.name(), tub.input(), result));
                resultBlocks.add(new ContentBlock.ToolResultBlock(tub.id(), toJson(result)));
            }

            messages.add(ClaudeMessage.toolResults(resultBlocks));
        }

        return new AgentResponse(lastText, toolSteps);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool result to JSON", e);
            return "{}";
        }
    }
}

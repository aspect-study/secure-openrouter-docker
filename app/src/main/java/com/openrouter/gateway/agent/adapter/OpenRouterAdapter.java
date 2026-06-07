package com.openrouter.gateway.agent.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.agent.model.AdapterResponse;
import com.openrouter.gateway.agent.model.ClaudeMessage;
import com.openrouter.gateway.agent.model.ContentBlock;
import com.openrouter.gateway.agent.model.StopReason;
import com.openrouter.gateway.agent.tool.GatewayTool;
import com.openrouter.gateway.config.AppProperties;
import com.openrouter.gateway.exception.ModelToolUseNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between Claude API vocabulary (used by AgentService) and the
 * OpenAI-compatible JSON format expected by the OpenRouter proxy.
 *
 * This is the ONLY class in the codebase that knows about OpenAI wire format.
 * Swapping to the Anthropic SDK means replacing this class only.
 */
@Component
public class OpenRouterAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterAdapter.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenRouterAdapter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.getOpenrouter().getProxyUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a chat completions request through the OpenRouter proxy and returns
     * the response translated back to Claude API vocabulary.
     *
     * @param messages conversation history in Claude format
     * @param tools    available tools the model may call
     * @param model    OpenRouter model id (e.g. "nvidia/nemotron-nano-9b-v2:free")
     * @param apiKey   the user's decrypted BYOK OpenRouter API key
     */
    public AdapterResponse call(List<ClaudeMessage> messages,
                                List<GatewayTool> tools,
                                String model,
                                String apiKey) {
        Map<String, Object> requestBody = buildRequestBody(messages, tools, model);
        log.debug("Sending agent request to OpenRouter: model={}, messages={}", model, messages.size());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            return parseResponse(response);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ModelToolUseNotSupportedException(model);
        }
    }

    /**
     * Builds the OpenAI-compatible request body map.
     * Visible for unit testing of the translation logic without HTTP.
     */
    public Map<String, Object> buildRequestBody(List<ClaudeMessage> messages,
                                                List<GatewayTool> tools,
                                                String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", convertMessages(messages));
        body.put("tools", convertTools(tools));
        body.put("tool_choice", "auto");
        body.put("stream", false);
        return body;
    }

    // ── Private: message conversion ────────────────────────────────────────

    private List<Map<String, Object>> convertMessages(List<ClaudeMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClaudeMessage msg : messages) {
            result.addAll(toOpenAiMessages(msg));
        }
        return result;
    }

    /**
     * One ClaudeMessage may expand into multiple OpenAI messages (e.g. a tool-results
     * message becomes one "tool" role message per result block).
     */
    private List<Map<String, Object>> toOpenAiMessages(ClaudeMessage msg) {
        List<ContentBlock> blocks = msg.content();

        // Tool result message: "user" role carrying ToolResultBlocks → OpenAI "tool" role messages
        boolean allToolResults = !blocks.isEmpty() && blocks.stream()
                .allMatch(b -> b instanceof ContentBlock.ToolResultBlock);
        if (allToolResults) {
            List<Map<String, Object>> toolMessages = new ArrayList<>();
            for (ContentBlock block : blocks) {
                ContentBlock.ToolResultBlock trb = (ContentBlock.ToolResultBlock) block;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", "tool");
                m.put("tool_call_id", trb.toolUseId());
                m.put("content", trb.content());
                toolMessages.add(m);
            }
            return toolMessages;
        }

        // Assistant message with tool_calls
        List<ContentBlock.ToolUseBlock> toolUseBlocks = blocks.stream()
                .filter(ContentBlock.ToolUseBlock.class::isInstance)
                .map(ContentBlock.ToolUseBlock.class::cast)
                .toList();

        if (!toolUseBlocks.isEmpty()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ContentBlock.ToolUseBlock tub : toolUseBlocks) {
                String argumentsJson;
                try {
                    argumentsJson = objectMapper.writeValueAsString(tub.input());
                } catch (JsonProcessingException e) {
                    argumentsJson = "{}";
                }
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", tub.name());
                fn.put("arguments", argumentsJson);

                Map<String, Object> toolCall = new LinkedHashMap<>();
                toolCall.put("id", tub.id());
                toolCall.put("type", "function");
                toolCall.put("function", fn);
                toolCalls.add(toolCall);
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", "assistant");
            m.put("content", null);
            m.put("tool_calls", toolCalls);
            return List.of(m);
        }

        // Plain text message (user or assistant)
        String text = blocks.stream()
                .filter(ContentBlock.TextBlock.class::isInstance)
                .map(ContentBlock.TextBlock.class::cast)
                .map(ContentBlock.TextBlock::text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", msg.role());
        m.put("content", text);
        return List.of(m);
    }

    // ── Private: tool conversion ───────────────────────────────────────────

    private List<Map<String, Object>> convertTools(List<GatewayTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GatewayTool tool : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.put("parameters", tool.inputSchema());

            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", "function");
            wrapper.put("function", fn);
            result.add(wrapper);
        }
        return result;
    }

    // ── Private: response parsing ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private AdapterResponse parseResponse(Map<String, Object> response) {
        if (response == null) {
            log.warn("OpenRouter returned null response body");
            return new AdapterResponse(StopReason.UNKNOWN, List.of());
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            log.warn("OpenRouter response contained no choices");
            return new AdapterResponse(StopReason.UNKNOWN, List.of());
        }

        Map<String, Object> choice = choices.get(0);
        String finishReason = (String) choice.get("finish_reason");
        StopReason stopReason = StopReason.fromOpenAiFinishReason(finishReason);

        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        List<ContentBlock> contentBlocks = new ArrayList<>();

        if (message != null) {
            Object contentRaw = message.get("content");
            if (contentRaw instanceof String text && !text.isBlank()) {
                contentBlocks.add(new ContentBlock.TextBlock(text));
            }

            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null) {
                for (Map<String, Object> tc : toolCalls) {
                    String id = (String) tc.get("id");
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    if (fn == null) continue;
                    String name = (String) fn.get("name");
                    String argumentsJson = (String) fn.get("arguments");
                    Map<String, Object> input = parseArguments(argumentsJson);
                    contentBlocks.add(new ContentBlock.ToolUseBlock(id, name, input));
                }
            }
        }

        log.debug("OpenRouter response parsed: stopReason={}, blocks={}", stopReason, contentBlocks.size());
        return new AdapterResponse(stopReason, contentBlocks);
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool call arguments JSON: {}", argumentsJson);
            return Map.of();
        }
    }
}

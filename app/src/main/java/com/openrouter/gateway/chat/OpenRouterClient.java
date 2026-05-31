package com.openrouter.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.config.AppProperties;
import com.openrouter.gateway.config.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client that calls the nginx OpenRouter proxy.
 *
 * Uses Java's built-in HttpClient (JDK 11+).
 * With virtual threads enabled, this blocking call runs on a virtual thread —
 * no reactive complexity, but high concurrency is maintained.
 *
 * The proxy handles token injection, so we send NO Authorization header here.
 *
 * Model validation delegates to ModelConfigService, which reads from the
 * model_config table (single source of truth). The enabled-model set is
 * cached in memory and evicted whenever an admin toggles a model.
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/api/v1/chat/completions";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String proxyUrl;
    private final ModelConfigService modelConfigService;

    public OpenRouterClient(HttpClient httpClient,
                             ObjectMapper objectMapper,
                             AppProperties appProperties,
                             ModelConfigService modelConfigService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.proxyUrl = appProperties.getOpenrouter().getProxyUrl();
        this.modelConfigService = modelConfigService;
    }

    /**
     * Validates the model against the DB-driven enabled list, then forwards
     * the chat request to the nginx proxy.
     *
     * @param requestBody raw JSON string from the client
     * @return ProxyResponse containing status code, body, and latency
     * @throws IllegalArgumentException if the model is not enabled in model_config
     */
    public ProxyResponse chat(String requestBody) throws Exception {
        // Parse and validate the model field before forwarding
        JsonNode root = objectMapper.readTree(requestBody);
        String model = root.path("model").asText();

        if (!modelConfigService.getEnabledModelIds().contains(model)) {
            throw new IllegalArgumentException(
                    "Model '%s' is not allowed or is currently disabled.".formatted(model));
        }

        log.info("Forwarding chat request for model: {}", model);

        long startMs = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(proxyUrl + CHAT_COMPLETIONS_PATH))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        long latencyMs = System.currentTimeMillis() - startMs;
        log.info("Proxy responded in {}ms with status {}", latencyMs, response.statusCode());

        return new ProxyResponse(response.statusCode(), response.body(), latencyMs);
    }

    /**
     * Parses usage stats from the OpenRouter response body.
     * Returns zeros if parsing fails — non-fatal.
     */
    public UsageStats parseUsage(String responseBody) {
        try {
            JsonNode usage = objectMapper.readTree(responseBody).path("usage");
            return new UsageStats(
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0)
            );
        } catch (Exception e) {
            log.warn("Failed to parse usage from response: {}", e.getMessage());
            return new UsageStats(0, 0, 0);
        }
    }

    /**
     * Parses the first assistant message content for logging preview.
     */
    public String parseResponsePreview(String responseBody) {
        try {
            return objectMapper.readTree(responseBody)
                    .path("choices").get(0)
                    .path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public record ProxyResponse(int statusCode, String body, long latencyMs) {}

    public record UsageStats(int promptTokens, int completionTokens, int totalTokens) {}
}

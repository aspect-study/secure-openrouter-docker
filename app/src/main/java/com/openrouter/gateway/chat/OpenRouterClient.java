package com.openrouter.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.config.AppProperties;
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
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/api/v1/chat/completions";

    // Whitelist of allowed free models — prevents users from requesting paid models.
    // Verified against OpenRouter API on 2026-05-29 using test-models.ps1.
    // Excludes: google/lyria-* (paid music models), minimax/minimax-m2.5:free (404).
    // 429s at test time are transient rate limits — those models are still valid.
    private static final java.util.Set<String> FREE_MODELS = java.util.Set.of(
            // NVIDIA
            "nvidia/nemotron-nano-9b-v2:free",
            "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
            "nvidia/nemotron-3-nano-30b-a3b:free",
            "nvidia/nemotron-nano-12b-v2-vl:free",
            // Meta
            "meta-llama/llama-3.3-70b-instruct:free",
            "meta-llama/llama-3.2-3b-instruct:free",
            // DeepSeek
            "deepseek/deepseek-v4-flash:free",
            // Qwen / Alibaba
            "qwen/qwen3-coder:free",
            "qwen/qwen3-next-80b-a3b-instruct:free",
            // Google
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            // OpenAI OSS
            "openai/gpt-oss-120b:free",
            "openai/gpt-oss-20b:free",
            // Poolside
            "poolside/laguna-xs.2:free",
            "poolside/laguna-m.1:free",
            // Liquid AI
            "liquid/lfm-2.5-1.2b-thinking:free",
            "liquid/lfm-2.5-1.2b-instruct:free",
            // Moonshot
            "moonshotai/kimi-k2.6:free",
            // Z-AI
            "z-ai/glm-4.5-air:free",
            // Cognitive Computations
            "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
            // NousResearch
            "nousresearch/hermes-3-llama-3.1-405b:free",
            // OpenRouter special routers
            "openrouter/owl-alpha",
            "openrouter/free"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String proxyUrl;

    public OpenRouterClient(HttpClient httpClient,
                             ObjectMapper objectMapper,
                             AppProperties appProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.proxyUrl = appProperties.getOpenrouter().getProxyUrl();
    }

    /**
     * Validates the model and forwards the chat request to the nginx proxy.
     *
     * @param requestBody raw JSON string from the client
     * @return ProxyResponse containing status code, body, and parsed usage
     * @throws IllegalArgumentException if the model is not in the free whitelist
     */
    public ProxyResponse chat(String requestBody) throws Exception {
        // Parse and validate the model field before forwarding
        JsonNode root = objectMapper.readTree(requestBody);
        String model = root.path("model").asText();

        if (!FREE_MODELS.contains(model)) {
            throw new IllegalArgumentException(
                    "Model '%s' is not allowed. Permitted free models: %s"
                            .formatted(model, FREE_MODELS));
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

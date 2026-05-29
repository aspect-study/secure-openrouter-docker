package com.openrouter.gateway.chat;

import com.openrouter.gateway.logging.ChatLog;
import com.openrouter.gateway.logging.ChatLogRepository;
import com.openrouter.gateway.ratelimit.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the chat request flow:
 *   1. Check rate limit
 *   2. Forward to OpenRouter via proxy
 *   3. Persist log entry
 *   4. Return response to controller
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final OpenRouterClient openRouterClient;
    private final RateLimitService rateLimitService;
    private final ChatLogRepository chatLogRepository;

    public ChatService(OpenRouterClient openRouterClient,
                       RateLimitService rateLimitService,
                       ChatLogRepository chatLogRepository) {
        this.openRouterClient = openRouterClient;
        this.rateLimitService = rateLimitService;
        this.chatLogRepository = chatLogRepository;
    }

    /**
     * Processes a chat completion request for the authenticated user.
     *
     * @param userEmail     authenticated user's email from JWT
     * @param requestBody   raw JSON request body from the client
     * @return ChatResult containing the proxy response and metadata
     */
    public ChatResult processChat(String userEmail, String requestBody) throws Exception {
        // ── 1. Rate limit check ──────────────────────────────────────────
        if (!rateLimitService.tryConsume(userEmail)) {
            long remaining = rateLimitService.availableTokens(userEmail);
            return ChatResult.rateLimited(remaining);
        }

        // ── 2. Forward to proxy ──────────────────────────────────────────
        OpenRouterClient.ProxyResponse proxyResponse = openRouterClient.chat(requestBody);

        // ── 3. Parse usage and response preview ──────────────────────────
        OpenRouterClient.UsageStats usage = openRouterClient.parseUsage(proxyResponse.body());
        String preview = openRouterClient.parseResponsePreview(proxyResponse.body());

        // Extract model from request body for logging
        String model = extractModel(requestBody);

        // ── 4. Persist chat log (async-friendly with virtual threads) ────
        try {
            ChatLog chatLog = ChatLog.of(
                    userEmail, model,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(),
                    proxyResponse.latencyMs(), proxyResponse.statusCode(), preview);
            chatLogRepository.save(chatLog);
        } catch (Exception e) {
            // Logging failure must not affect the user response
            log.error("Failed to persist chat log for user {}: {}", userEmail, e.getMessage());
        }

        long remainingTokens = rateLimitService.availableTokens(userEmail);
        return ChatResult.success(proxyResponse.statusCode(), proxyResponse.body(), remainingTokens);
    }

    // ── Private ───────────────────────────────────────────────────────────

    private String extractModel(String requestBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(requestBody).path("model").asText("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ── Result types (sealed interface + records — Java 17+) ─────────────

    public sealed interface ChatResult permits ChatResult.Success, ChatResult.RateLimited {

        record Success(int statusCode, String body, long remainingTokens) implements ChatResult {}

        record RateLimited(long remainingTokens) implements ChatResult {}

        static ChatResult success(int statusCode, String body, long remaining) {
            return new Success(statusCode, body, remaining);
        }

        static ChatResult rateLimited(long remaining) {
            return new RateLimited(remaining);
        }
    }
}

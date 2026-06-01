package com.openrouter.gateway.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.apikey.OpenRouterKeyService;
import com.openrouter.gateway.logging.ChatLog;
import com.openrouter.gateway.logging.ChatLogRepository;
import com.openrouter.gateway.ratelimit.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the chat request flow:
 *   1. Check Bucket4j rate limit
 *   2. Resolve user API key (BYOK)
 *   3. Forward to OpenRouter via proxy
 *   4. Parse usage from response
 *   5. Persist chat log
 *   6. Return response to controller
 *
 * Usage limit enforcement removed — each user authenticates with their own
 * OpenRouter API key (BYOK). OpenRouter enforces per-key rate limits upstream.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final OpenRouterClient openRouterClient;
    private final RateLimitService rateLimitService;
    private final ChatLogRepository chatLogRepository;
    private final OpenRouterKeyService openRouterKeyService;
    private final ObjectMapper objectMapper;

    public ChatService(OpenRouterClient openRouterClient,
                       RateLimitService rateLimitService,
                       ChatLogRepository chatLogRepository,
                       OpenRouterKeyService openRouterKeyService,
                       ObjectMapper objectMapper) {
        this.openRouterClient = openRouterClient;
        this.rateLimitService = rateLimitService;
        this.chatLogRepository = chatLogRepository;
        this.openRouterKeyService = openRouterKeyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a chat completion request for the authenticated user.
     *
     * @param userEmail   authenticated user's email from JWT
     * @param requestBody raw JSON request body from the client
     * @return ChatResult containing the proxy response and metadata
     */
    public ChatResult processChat(String userEmail, String requestBody) throws Exception {
        // ── 1. Rate limit check (Bucket4j in-memory per-minute) ─────────────
        if (!rateLimitService.tryConsume(userEmail)) {
            long remaining = rateLimitService.availableTokens(userEmail);
            return ChatResult.rateLimited(remaining);
        }

        // ── 2. Resolve API key (throws KeyNotConfiguredException if not set) ──
        String apiKey = openRouterKeyService.getKeyForUser(userEmail);
        String model = extractModel(requestBody);

        // ── 3. Forward to proxy with user's own key ──────────────────────────
        OpenRouterClient.ProxyResponse proxyResponse = openRouterClient.chat(requestBody, apiKey);

        // ── 4. Parse usage + persist chat log ────────────────────────────────
        OpenRouterClient.UsageStats usage = openRouterClient.parseUsage(proxyResponse.body());
        String preview = openRouterClient.parseResponsePreview(proxyResponse.body());

        try {
            ChatLog chatLog = ChatLog.of(
                    userEmail, model,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(),
                    proxyResponse.latencyMs(), proxyResponse.statusCode(), preview);
            chatLogRepository.save(chatLog);
        } catch (Exception e) {
            log.error("Failed to persist chat log for user {}: {}", userEmail, e.getMessage());
        }

        long remainingTokens = rateLimitService.availableTokens(userEmail);
        return ChatResult.success(proxyResponse.statusCode(), proxyResponse.body(), remainingTokens);
    }

    // ── Private ───────────────────────────────────────────────────────────

    private String extractModel(String requestBody) {
        try {
            return objectMapper.readTree(requestBody).path("model").asText("unknown");
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

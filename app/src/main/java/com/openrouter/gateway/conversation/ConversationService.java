package com.openrouter.gateway.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.apikey.OpenRouterKeyService;
import com.openrouter.gateway.auth.UserRepository;
import com.openrouter.gateway.chat.OpenRouterClient;
import com.openrouter.gateway.logging.ChatLog;
import com.openrouter.gateway.logging.ChatLogRepository;
import com.openrouter.gateway.ratelimit.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Service layer for streaming conversation operations.
 *
 * Separates the streaming business logic from the controller to keep
 * ConversationController focused on HTTP concerns (SseEmitter lifecycle,
 * thread management, event serialization).
 *
 * The non-streaming path remains in ConversationController directly
 * for backward compatibility — no changes to that flow.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final OpenRouterClient openRouterClient;
    private final RateLimitService rateLimitService;
    private final ChatLogRepository chatLogRepository;
    private final ObjectMapper objectMapper;
    private final OpenRouterKeyService openRouterKeyService;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               OpenRouterClient openRouterClient,
                               RateLimitService rateLimitService,
                               ChatLogRepository chatLogRepository,
                               ObjectMapper objectMapper,
                               OpenRouterKeyService openRouterKeyService,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.openRouterClient = openRouterClient;
        this.rateLimitService = rateLimitService;
        this.chatLogRepository = chatLogRepository;
        this.objectMapper = objectMapper;
        this.openRouterKeyService = openRouterKeyService;
        this.userRepository = userRepository;
    }

    /**
     * Validates conversation ownership, checks rate limit, persists the user message,
     * then initiates a streaming request to OpenRouter.
     *
     * The assistant message is NOT persisted here — the controller assembles the full
     * content from streamed tokens and calls persistAssistantMessage() after completion.
     *
     * @param conversationId target conversation
     * @param userEmail      authenticated user — used for ownership check and rate limiting
     * @param content        user message text
     * @param chunkConsumer  receives each raw SSE delta JSON chunk string
     * @throws NoSuchElementException if conversation not found or not owned by userEmail
     * @throws RateLimitExceededException if the user is rate limited
     * @throws Exception on OpenRouter or I/O failure
     */
    @Transactional
    public void streamMessage(Long conversationId,
                              String userEmail,
                              String content,
                              Consumer<String> chunkConsumer) throws Exception {

        // ── 1. Ownership check ───────────────────────────────────────────────
        Conversation conversation = conversationRepository
                .findByIdAndUserEmail(conversationId, userEmail)
                .orElseThrow(() -> new NoSuchElementException(
                        "Conversation %d not found for user %s".formatted(conversationId, userEmail)));

        // ── 2. Rate limit check ──────────────────────────────────────────────
        if (!rateLimitService.tryConsume(userEmail)) {
            long remaining = rateLimitService.availableTokens(userEmail);
            throw new RateLimitExceededException(remaining);
        }

        // ── 2b. Resolve API key (throws KeyNotConfiguredException if not set) ──
        // Each user has their own OpenRouter key (BYOK) — OpenRouter enforces limits upstream.
        String apiKey = openRouterKeyService.getKeyForUser(userEmail);

        // ── 3. Persist user message ──────────────────────────────────────────
        ConversationMessage userMsg = new ConversationMessage(
                conversation, ConversationMessage.Role.user, content);
        conversation.getMessages().add(userMsg);

        // Auto-title on first real message (first 60 chars of input)
        if (conversation.getMessages().size() == 1 &&
                "New Conversation".equals(conversation.getTitle())) {
            String title = content.length() > 60
                    ? content.substring(0, 60) + "..."
                    : content;
            conversation.setTitle(title);
        }

        conversationRepository.saveAndFlush(conversation);

        // ── 4. Build OpenRouter request (message history for context) ────────
        List<Map<String, String>> messages = buildMessages(conversation);

        // stream:true is injected inside OpenRouterClient.streamChatCompletion()
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", conversation.getModel(),
                "messages", messages));

        // ── 5. Delegate to OpenRouterClient — blocking stream on calling thread ──
        openRouterClient.streamChatCompletion(requestBody, apiKey, chunkConsumer);
    }

    /**
     * Persists the fully assembled assistant response to conversation_messages.
     * Called by the controller after the stream completes successfully.
     *
     * @param conversationId target conversation (no ownership check — internal call)
     * @param content        fully assembled assistant response text
     * @return the saved ConversationMessage (for inclusion in the "done" SSE event)
     */
    @Transactional
    public ConversationMessage persistAssistantMessage(Long conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Conversation %d not found during assistant persist".formatted(conversationId)));

        ConversationMessage assistantMsg = new ConversationMessage(
                conversation, ConversationMessage.Role.assistant, content);
        conversation.getMessages().add(assistantMsg);
        conversationRepository.saveAndFlush(conversation);

        log.debug("Persisted assistant message ({} chars) to conversation {}",
                content.length(), conversationId);
        return assistantMsg;
    }

    /**
     * Persists a chat log entry for a completed streaming request.
     * Non-fatal — logging failure must not affect the user experience.
     *
     * @param userEmail        authenticated user
     * @param model            model ID used for the request
     * @param promptTokens     from the final SSE chunk's usage field (0 if unavailable)
     * @param completionTokens from the final SSE chunk's usage field (0 if unavailable)
     * @param latencyMs        total stream duration in milliseconds
     * @param responsePreview  first portion of the assembled response for quick inspection
     */
    public void logStreamChat(String userEmail, String model,
                              int promptTokens, int completionTokens,
                              long latencyMs, String responsePreview) {
        try {
            int totalTokens = promptTokens + completionTokens;

            ChatLog chatLog = ChatLog.of(
                    userEmail, model,
                    promptTokens, completionTokens, totalTokens,
                    latencyMs, 200, responsePreview);
            chatLogRepository.save(chatLog);
        } catch (Exception e) {
            log.error("Failed to persist stream chat log for user {}: {}", userEmail, e.getMessage());
        }
    }

    // ── Message builder ───────────────────────────────────────────────────────

    /**
     * Builds the full message list for an OpenRouter request.
     *
     * A formatting system prompt is prepended as the first message so every model,
     * regardless of size or provider, produces clean GFM-compliant markdown.
     * This is the highest-leverage fix: preventing broken output at the source
     * rather than post-processing it downstream.
     *
     * The system message is injected here (not stored in conversation_messages)
     * so it never appears in the conversation history shown to the user.
     */
    private List<Map<String, String>> buildMessages(Conversation conversation) {
        List<Map<String, String>> messages = new ArrayList<>();

        // Formatting system prompt — applies to all models
        messages.add(Map.of(
                "role", "system",
                "content", FORMATTING_SYSTEM_PROMPT
        ));

        // Full conversation history
        conversation.getMessages().forEach(m ->
                messages.add(Map.of("role", m.getRole().name(), "content", m.getContent())));

        return messages;
    }

    /**
     * Universal markdown formatting rules injected as a system prompt.
     *
     * Rules are explicit and example-driven for small models (< 8B) which
     * require more guidance than larger ones. Larger models respect these rules
     * with minimal overhead.
     */
    private static final String FORMATTING_SYSTEM_PROMPT = """
            You are a helpful assistant. Always format your responses using clean, \
            universally compatible Markdown (CommonMark + GFM).

            MARKDOWN RULES — follow these strictly:

            TABLES:
            - Every table must have: header row | separator row (---) | data rows
            - Each row on its own line. Space inside every cell: | Cell text |
            - Separator row example: | --- | --- | --- |
            - Keep column count identical across all rows
            - Example:
              | Day | Activity | Fun Fact |
              | --- | --- | --- |
              | 1 | Visit museum | Founded in 1900 |

            LISTS:
            - Use - for unordered lists, 1. 2. 3. for ordered
            - One blank line before any list that follows a paragraph
            - One item per line

            CODE:
            - Always use fenced code blocks with a language tag: ```python
            - Inline code uses single backticks: `variable`

            TEXT:
            - Separate paragraphs with one blank line
            - Use **bold** and *italic* sparingly for emphasis
            - Never concatenate words — always space between words
            - Keep sentences clear and human-readable

            HEADINGS:
            - Use # ## ### for hierarchy
            - One blank line before and after each heading

            Do not use HTML tags. Do not use proprietary formatting.
            """;

    // ── Inner exception type ──────────────────────────────────────────────────

    public static class RateLimitExceededException extends RuntimeException {
        private final long remainingTokens;

        public RateLimitExceededException(long remainingTokens) {
            super("Rate limit exceeded");
            this.remainingTokens = remainingTokens;
        }

        public long getRemainingTokens() { return remainingTokens; }
    }
}

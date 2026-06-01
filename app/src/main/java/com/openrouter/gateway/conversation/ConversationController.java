package com.openrouter.gateway.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.chat.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Manages persistent chat conversations for the AI Playground.
 *
 * GET    /api/conversations                      — list user's conversations
 * POST   /api/conversations                      — create new conversation
 * GET    /api/conversations/{id}                 — get conversation with messages
 * POST   /api/conversations/{id}/messages        — send message (blocking, full response)
 * POST   /api/conversations/{id}/messages/stream — send message (SSE streaming)
 * DELETE /api/conversations/{id}                 — delete conversation
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationRepository conversationRepository;
    private final ChatService chatService;
    private final ConversationService conversationService;
    private final MarkdownNormalizer markdownNormalizer;
    private final ObjectMapper objectMapper;

    public ConversationController(ConversationRepository conversationRepository,
                                   ChatService chatService,
                                   ConversationService conversationService,
                                   MarkdownNormalizer markdownNormalizer,
                                   ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.markdownNormalizer = markdownNormalizer;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<ConversationDto.Summary>> list(
            @AuthenticationPrincipal String userEmail) {
        List<ConversationDto.Summary> result = conversationRepository
                .findByUserEmailOrderByUpdatedAtDesc(userEmail)
                .stream()
                .map(ConversationDto.Summary::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<ConversationDto.Detail> create(
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody ConversationDto.CreateRequest req) {
        Conversation conversation = new Conversation(userEmail, req.model());
        conversation.setTitle(req.title() != null ? req.title() : "New Conversation");
        Conversation saved = conversationRepository.save(conversation);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConversationDto.Detail.from(saved));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ConversationDto.Detail> get(
            @AuthenticationPrincipal String userEmail,
            @PathVariable Long id) {
        return conversationRepository.findByIdAndUserEmail(id, userEmail)
                .map(c -> ResponseEntity.ok(ConversationDto.Detail.from(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/messages")
    @Transactional
    public ResponseEntity<Object> sendMessage(
            @AuthenticationPrincipal String userEmail,
            @PathVariable Long id,
            @Valid @RequestBody ConversationDto.MessageRequest req) {

        Conversation conversation = conversationRepository
                .findByIdAndUserEmail(id, userEmail)
                .orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        // Save user message
        ConversationMessage userMsg = new ConversationMessage(
                conversation, ConversationMessage.Role.user, req.content());
        conversation.getMessages().add(userMsg);

        // Auto-title on first message (first 60 chars of user input)
        if (conversation.getMessages().size() == 1 &&
                "New Conversation".equals(conversation.getTitle())) {
            String title = req.content().length() > 60
                    ? req.content().substring(0, 60) + "..."
                    : req.content();
            conversation.setTitle(title);
        }

        // Build full message history for context
        List<Map<String, String>> messages = conversation.getMessages().stream()
                .map(m -> Map.of("role", m.getRole().name(), "content", m.getContent()))
                .toList();

        // Build OpenRouter request body
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", conversation.getModel(),
                    "messages", messages));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to build request"));
        }

        // Forward to OpenRouter via ChatService
        try {
            ChatService.ChatResult result = chatService.processChat(userEmail, requestBody);

            return switch (result) {
                case ChatService.ChatResult.Success s -> {
                    // Non-2xx from OpenRouter (e.g. 429, 503) — roll back user message, return error
                    if (s.statusCode() >= 400) {
                        conversation.getMessages().remove(conversation.getMessages().size() - 1);
                        conversationRepository.save(conversation);
                        String errorMsg = parseOpenRouterError(s.body());
                        yield ResponseEntity.status(s.statusCode())
                                .body(Map.of("error", errorMsg, "statusCode", s.statusCode()));
                    }

                    // Parse assistant response, normalize markdown, then persist
                    String assistantContent = markdownNormalizer.normalize(extractContent(s.body()));
                    ConversationMessage assistantMsg = new ConversationMessage(
                            conversation, ConversationMessage.Role.assistant, assistantContent);
                    conversation.getMessages().add(assistantMsg);
                    conversationRepository.saveAndFlush(conversation);

                    // Parse real token usage from OpenRouter response
                    Map<String, Integer> usage = parseUsage(s.body());

                    yield ResponseEntity.ok(Map.of(
                            "message", ConversationDto.MessageDto.from(assistantMsg),
                            "conversationId", conversation.getId(),
                            "title", conversation.getTitle(),
                            "usage", usage
                    ));
                }
                case ChatService.ChatResult.RateLimited r -> ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Rate limit exceeded", "remainingTokens", r.remainingTokens()));
            };

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing message for conversation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * POST /api/conversations/{id}/messages/stream
     *
     * Streams the assistant response as Server-Sent Events (SSE).
     * The existing /messages endpoint is unchanged — this is an additive endpoint.
     *
     * SSE event protocol:
     *   event: token   data: <plain text token>
     *   event: done    data: {"messageId":1,"conversationId":1,"title":"...","usage":{...}}
     *
     * Rate limit is checked synchronously before spawning the virtual thread so that
     * a 429 response can be returned before the SSE connection is opened.
     *
     * The user message is persisted in ConversationService.streamMessage() before streaming
     * starts. The assistant message is persisted after the stream completes successfully.
     * A mid-stream client disconnect leaves the user message in DB but no assistant message —
     * this is intentional (we don't persist a partial/unknown response).
     */
    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object streamMessage(
            @AuthenticationPrincipal String userEmail,
            @PathVariable Long id,
            @Valid @RequestBody ConversationDto.MessageRequest req) {

        // Verify conversation exists before opening SSE connection
        boolean exists = conversationRepository.findByIdAndUserEmail(id, userEmail).isPresent();
        if (!exists) {
            return ResponseEntity.notFound().build();
        }

        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.ofVirtual().start(() -> {
            StringBuilder assembled = new StringBuilder();
            long startMs = System.currentTimeMillis();

            // Capture usage from the final SSE chunk (finish_reason=stop includes usage)
            final int[] promptTokens     = {0};
            final int[] completionTokens = {0};

            // Model for logging — loaded once stream starts
            final String[] model = {""};

            try {
                // Load model for logging (conversation ownership already verified above)
                model[0] = conversationRepository.findByIdAndUserEmail(id, userEmail)
                        .map(Conversation::getModel).orElse("unknown");

                conversationService.streamMessage(id, userEmail, req.content(), chunk -> {
                    try {
                        JsonNode chunkNode = objectMapper.readTree(chunk);

                        // Extract delta content token
                        JsonNode choices = chunkNode.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            String token = choices.get(0)
                                    .path("delta").path("content").asText("");

                            if (!token.isEmpty()) {
                                assembled.append(token);
                                // JSON-encode the token so whitespace characters (\n, \t, etc.)
                                // survive SSE transport. Raw \n in SSE data: fields is treated
                                // as an empty line by the protocol, silently dropping newlines
                                // and collapsing multi-line responses (tables, code) into one line.
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data(objectMapper.writeValueAsString(token)));
                            }

                            // Capture usage from the final chunk (present when finish_reason=stop)
                            JsonNode usage = chunkNode.path("usage");
                            if (!usage.isMissingNode() && !usage.isNull()) {
                                promptTokens[0]     = usage.path("prompt_tokens").asInt(0);
                                completionTokens[0] = usage.path("completion_tokens").asInt(0);
                            }
                        }
                    } catch (IOException e) {
                        // Client disconnected mid-stream — abort the stream
                        log.warn("SSE client disconnected during stream for conversation {}: {}",
                                id, e.getMessage());
                        throw new RuntimeException("SSE send failed (client disconnect)", e);
                    } catch (Exception e) {
                        log.warn("Failed to parse stream chunk: {}", e.getMessage());
                        // Non-fatal: skip malformed chunk and continue
                    }
                });

                // Normalize the assembled response before persisting.
                // This is the backend's last chance to fix table structure,
                // missing separator rows, and spacing before the text is stored.
                String normalizedContent = markdownNormalizer.normalize(assembled.toString());

                // Stream completed — persist assistant message and send done event
                ConversationMessage saved = conversationService.persistAssistantMessage(
                        id, normalizedContent);

                long latencyMs = System.currentTimeMillis() - startMs;
                conversationService.logStreamChat(
                        userEmail, model[0],
                        promptTokens[0], completionTokens[0],
                        latencyMs, assembled.toString());

                // Retrieve updated title (may have been auto-set on first message)
                String title = conversationRepository.findById(id)
                        .map(Conversation::getTitle).orElse("");

                String donePayload = objectMapper.writeValueAsString(Map.of(
                        "messageId",      saved.getId() != null ? saved.getId() : 0,
                        "conversationId", id,
                        "title",          title,
                        "normalizedContent", normalizedContent,
                        "usage",          Map.of(
                                "promptTokens",     promptTokens[0],
                                "completionTokens", completionTokens[0],
                                "totalTokens",      promptTokens[0] + completionTokens[0]
                        )
                ));

                emitter.send(SseEmitter.event().name("done").data(donePayload));
                emitter.complete();

            } catch (ConversationService.RateLimitExceededException e) {
                try {
                    String payload = objectMapper.writeValueAsString(
                            Map.of("error", "Rate limit exceeded",
                                   "remainingTokens", e.getRemainingTokens()));
                    emitter.send(SseEmitter.event().name("error").data(payload));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);

            } catch (NoSuchElementException e) {
                emitter.completeWithError(e);

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isUpstream429 = msg.contains("stream error 429");
                boolean isUpstream404 = msg.contains("stream error 404");

                if (isUpstream429 || isUpstream404) {
                    log.warn("Upstream {} for conversation {} user {}: {}",
                            isUpstream429 ? "429" : "404", id, userEmail, msg);
                } else {
                    log.error("SSE stream error for conversation {} user {}: {}",
                            id, userEmail, msg, e);
                }
                try {
                    String userMessage = isUpstream429
                            ? "This model is temporarily rate-limited by the provider. Please wait a moment or switch to a different model."
                            : isUpstream404
                            ? "This model is no longer available on OpenRouter's free tier. Please switch to a different model."
                            : "Stream failed. Please try again.";
                    String payload = objectMapper.writeValueAsString(
                            Map.of("error", userMessage, "remainingTokens", 0));
                    emitter.send(SseEmitter.event().name("error").data(payload));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal String userEmail,
            @PathVariable Long id) {
        Conversation conversation = conversationRepository
                .findByIdAndUserEmail(id, userEmail).orElse(null);
        if (conversation == null) return ResponseEntity.notFound().build();
        conversationRepository.delete(conversation);
        return ResponseEntity.noContent().build();
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0)
                    .path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String parseOpenRouterError(String responseBody) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseBody);
            // OpenRouter wraps errors as { "error": { "message": "...", "code": ... } }
            com.fasterxml.jackson.databind.JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String msg = error.path("message").asText("");
                // Strip verbose upstream details — keep first sentence only
                int dot = msg.indexOf('.');
                return dot > 0 ? msg.substring(0, dot + 1) : (msg.isEmpty() ? "OpenRouter error" : msg);
            }
            return "OpenRouter returned an error";
        } catch (Exception e) {
            return "OpenRouter returned an error";
        }
    }

    private Map<String, Integer> parseUsage(String responseBody) {
        try {
            JsonNode usage = objectMapper.readTree(responseBody).path("usage");
            return Map.of(
                    "promptTokens",     usage.path("prompt_tokens").asInt(0),
                    "completionTokens", usage.path("completion_tokens").asInt(0),
                    "totalTokens",      usage.path("total_tokens").asInt(0)
            );
        } catch (Exception e) {
            return Map.of("promptTokens", 0, "completionTokens", 0, "totalTokens", 0);
        }
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public static class ConversationDto {

        public record Summary(Long id, String title, String model,
                              String updatedAt) {
            public static Summary from(Conversation c) {
                return new Summary(c.getId(), c.getTitle(), c.getModel(),
                        c.getUpdatedAt().toString());
            }
        }

        public record Detail(Long id, String title, String model,
                             List<MessageDto> messages, String createdAt) {
            public static Detail from(Conversation c) {
                return new Detail(c.getId(), c.getTitle(), c.getModel(),
                        c.getMessages().stream().map(MessageDto::from).toList(),
                        c.getCreatedAt().toString());
            }
        }

        public record MessageDto(Long id, String role, String content, String createdAt) {
            public static MessageDto from(ConversationMessage m) {
                // createdAt is @CreationTimestamp — null before Hibernate flush
                String ts = m.getCreatedAt() != null
                        ? m.getCreatedAt().toString()
                        : java.time.LocalDateTime.now().toString();
                return new MessageDto(m.getId(), m.getRole().name(), m.getContent(), ts);
            }
        }

        public record CreateRequest(
                @NotBlank String model,
                String title
        ) {}

        public record MessageRequest(
                @NotBlank @Size(max = 32000) String content
        ) {}
    }
}

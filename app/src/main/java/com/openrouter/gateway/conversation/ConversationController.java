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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manages persistent chat conversations for the AI Playground.
 *
 * GET    /api/conversations              — list user's conversations
 * POST   /api/conversations              — create new conversation
 * GET    /api/conversations/{id}         — get conversation with messages
 * POST   /api/conversations/{id}/messages — send message (calls OpenRouter)
 * DELETE /api/conversations/{id}         — delete conversation
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationRepository conversationRepository;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ConversationController(ConversationRepository conversationRepository,
                                   ChatService chatService,
                                   ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatService = chatService;
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
    public ResponseEntity<ConversationDto.Detail> get(
            @AuthenticationPrincipal String userEmail,
            @PathVariable Long id) {
        return conversationRepository.findByIdAndUserEmail(id, userEmail)
                .map(c -> ResponseEntity.ok(ConversationDto.Detail.from(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/messages")
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

                    // Parse assistant response and persist
                    String assistantContent = extractContent(s.body());
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

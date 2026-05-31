package com.openrouter.gateway.chat;

import com.openrouter.gateway.config.ModelConfigService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint for chat completions.
 *
 * POST /api/chat/completions
 *   - Requires valid JWT (enforced by SecurityConfig)
 *   - Forwards to OpenRouter via nginx proxy
 *   - Rate limited per user (Bucket4j)
 *   - Logs every request to MySQL
 *
 * GET /api/chat/models
 *   - Returns enabled models from model_config table (cached, no hardcoded list)
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ModelConfigService modelConfigService;

    public ChatController(ChatService chatService, ModelConfigService modelConfigService) {
        this.chatService = chatService;
        this.modelConfigService = modelConfigService;
    }

    /**
     * @param userEmail  injected from JWT via @AuthenticationPrincipal (the principal is set
     *                   to the email string in JwtAuthFilter)
     * @param requestBody raw JSON — passed through to OpenRouter as-is after model validation
     */
    @PostMapping("/completions")
    public ResponseEntity<Object> completions(
            @AuthenticationPrincipal String userEmail,
            @RequestBody @NotBlank String requestBody) {

        log.info("Chat request from user: {}", userEmail);

        try {
            ChatService.ChatResult result = chatService.processChat(userEmail, requestBody);

            // Pattern matching on sealed interface (Java 21+)
            return switch (result) {
                case ChatService.ChatResult.Success s -> ResponseEntity
                        .status(s.statusCode())
                        .header("X-RateLimit-Remaining", String.valueOf(s.remainingTokens()))
                        .body(s.body());

                case ChatService.ChatResult.RateLimited r -> ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("X-RateLimit-Remaining", "0")
                        .header("Retry-After", "60")
                        .body(Map.of(
                                "error", "Rate limit exceeded",
                                "message", "You have exceeded the request limit. Please wait 60 seconds.",
                                "remainingTokens", r.remainingTokens()
                        ));
            };

        } catch (IllegalArgumentException e) {
            // Model not enabled in model_config
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid model", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error processing chat request for {}: {}", userEmail, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Returns the list of currently enabled free models from model_config.
     * Backed by the same @Cacheable cache used for validation — no extra DB hit.
     */
    @GetMapping("/models")
    public ResponseEntity<Object> allowedModels() {
        List<String> models = modelConfigService.getEnabledModelIds()
                .stream()
                .sorted()
                .toList();
        return ResponseEntity.ok(Map.of("models", models));
    }
}

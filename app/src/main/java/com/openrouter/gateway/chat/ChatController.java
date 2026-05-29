package com.openrouter.gateway.chat;

import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint for chat completions.
 *
 * POST /api/chat/completions
 *   - Requires valid JWT (enforced by SecurityConfig)
 *   - Forwards to OpenRouter via nginx proxy
 *   - Rate limited per user (Bucket4j)
 *   - Logs every request to MySQL
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
            // Model not in whitelist
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid model", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error processing chat request for {}: {}", userEmail, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Returns the list of allowed free models.
     */
    @GetMapping("/models")
    public ResponseEntity<Object> allowedModels() {
        return ResponseEntity.ok(Map.of(
                "models", java.util.List.of(
                        "nvidia/nemotron-nano-9b-v2:free",
                        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
                        "nvidia/nemotron-3-super-120b-a12b:free",
                        "nvidia/nemotron-3-nano-30b-a3b:free",
                        "nvidia/nemotron-nano-12b-v2-vl:free",
                        "meta-llama/llama-3.3-70b-instruct:free",
                        "meta-llama/llama-3.2-3b-instruct:free",
                        "deepseek/deepseek-v4-flash:free",
                        "qwen/qwen3-coder:free",
                        "qwen/qwen3-next-80b-a3b-instruct:free",
                        "google/gemma-4-31b-it:free",
                        "google/gemma-4-26b-a4b-it:free",
                        "openai/gpt-oss-120b:free",
                        "openai/gpt-oss-20b:free",
                        "poolside/laguna-xs.2:free",
                        "poolside/laguna-m.1:free",
                        "liquid/lfm-2.5-1.2b-thinking:free",
                        "liquid/lfm-2.5-1.2b-instruct:free",
                        "moonshotai/kimi-k2.6:free",
                        "z-ai/glm-4.5-air:free",
                        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
                        "nousresearch/hermes-3-llama-3.1-405b:free",
                        "openrouter/owl-alpha",
                        "openrouter/free"
                )
        ));
    }
}

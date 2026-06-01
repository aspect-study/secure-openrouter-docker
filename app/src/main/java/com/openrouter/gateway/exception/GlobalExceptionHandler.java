package com.openrouter.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.Map;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;

/**
 * Centralized exception handling for all controllers.
 * Returns consistent JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles @Valid / @Validated constraint violations.
     * Returns 400 with a map of field -> error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value",
                        (a, b) -> a // keep first error if duplicate field
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Validation failed", "fields", fieldErrors));
    }

    /**
     * Invalid OpenRouter API key submitted by the user — 400.
     */
    @ExceptionHandler(InvalidApiKeyException.class)
    public ResponseEntity<Map<String, String>> handleInvalidApiKey(InvalidApiKeyException ex) {
        log.warn("Invalid API key submission: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * User tried to chat without configuring their API key — 409.
     */
    @ExceptionHandler(KeyNotConfiguredException.class)
    public ResponseEntity<Map<String, String>> handleKeyNotConfigured(KeyNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Daily usage limit exceeded — 429 with reset time.
     */
    @ExceptionHandler(UsageLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUsageLimitExceeded(UsageLimitExceededException ex) {
        log.info("Usage limit exceeded: {} for model {}, resets at {}",
                ex.getLimitType(), ex.getModelId(), ex.getResetAt());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "error",     ex.getMessage(),
                        "limitType", ex.getLimitType(),
                        "modelId",   ex.getModelId(),
                        "resetAt",   ex.getResetAt().format(DateTimeFormatter.ISO_DATE_TIME)
                ));
    }

    /**
     * Suppress noise from SSE client disconnects.
     *
     * Spring 6 throws AsyncRequestNotUsableException when the client drops a streaming
     * connection (e.g., the browser navigates away mid-stream). The SseEmitter in
     * ConversationController already handles this inline via completeWithError().
     * Without this handler the exception would bubble here and log a spurious 500.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        // Client disconnected mid-stream — handled inline by the SseEmitter; nothing to do here.
        log.debug("SSE client disconnected (AsyncRequestNotUsableException): {}", ex.getMessage());
    }

    /**
     * Catch-all for unhandled exceptions — prevents stack traces leaking to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }
}

package com.openrouter.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.Map;
import java.util.stream.Collectors;

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
     * Model not found by integer PK — 404.
     */
    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleModelNotFound(ModelNotFoundException ex) {
        log.warn("Model not found: id={}", ex.getModelConfigId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage(), "modelConfigId", ex.getModelConfigId()));
    }

    /**
     * User attempted to toggle an admin-disabled model — 400.
     */
    @ExceptionHandler(ModelAdminDisabledException.class)
    public ResponseEntity<Map<String, String>> handleModelAdminDisabled(ModelAdminDisabledException ex) {
        log.warn("Toggle rejected — admin-disabled model: {}", ex.getModelId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage(), "modelId", ex.getModelId()));
    }

    /**
     * 403 for callers hitting admin endpoints without ROLE_ADMIN.
     *
     * AuthProvider probes /api/admin/stats after every login to detect admin status.
     * Regular users always get 403 here — this is expected, not an error.
     * Logged at DEBUG to avoid noise in production logs.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AuthorizationDeniedException ex) {
        log.debug("Access denied (expected for non-admin probe): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied"));
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

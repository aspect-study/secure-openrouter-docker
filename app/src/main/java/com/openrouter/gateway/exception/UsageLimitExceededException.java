package com.openrouter.gateway.exception;

import java.time.LocalDateTime;

/**
 * Thrown when a user has exhausted their daily usage limit for a model.
 * Maps to HTTP 429 Too Many Requests.
 *
 * limitType: "requests" — request count limit reached (pre-call check)
 *            "tokens"   — token count soft limit reached (post-call warning, hard-blocks next call)
 */
public class UsageLimitExceededException extends RuntimeException {

    private final String limitType;       // "requests" or "tokens"
    private final String modelId;
    private final LocalDateTime resetAt;

    public UsageLimitExceededException(String limitType, String modelId, LocalDateTime resetAt) {
        super("Daily %s limit reached for model %s. Resets at %s."
                .formatted(limitType, modelId, resetAt));
        this.limitType = limitType;
        this.modelId = modelId;
        this.resetAt = resetAt;
    }

    public String getLimitType() { return limitType; }
    public String getModelId()   { return modelId; }
    public LocalDateTime getResetAt() { return resetAt; }
}

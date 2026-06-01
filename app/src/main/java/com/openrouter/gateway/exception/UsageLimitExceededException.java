package com.openrouter.gateway.exception;

/**
 * Retained for compilation only.
 * Usage limit enforcement removed — each user uses their own OpenRouter API key (BYOK).
 * OpenRouter enforces per-key rate limits upstream; application-level enforcement is redundant.
 */
@Deprecated
public class UsageLimitExceededException extends RuntimeException {
    public UsageLimitExceededException(String message) { super(message); }
}

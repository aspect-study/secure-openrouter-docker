package com.openrouter.gateway.exception;

/**
 * Thrown when a user submits an OpenRouter API key that fails live validation.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException(String message) {
        super(message);
    }
}

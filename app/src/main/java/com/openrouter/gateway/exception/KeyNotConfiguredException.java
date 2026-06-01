package com.openrouter.gateway.exception;

/**
 * Thrown when a user attempts to chat but has not saved an OpenRouter API key.
 * Maps to HTTP 409 Conflict with a message directing the user to Settings.
 */
public class KeyNotConfiguredException extends RuntimeException {

    public KeyNotConfiguredException() {
        super("Please add your OpenRouter API key in Settings to start chatting.");
    }
}

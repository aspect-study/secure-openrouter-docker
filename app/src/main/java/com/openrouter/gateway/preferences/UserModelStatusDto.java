package com.openrouter.gateway.preferences;

/**
 * Response for toggle and status endpoints — reflects the current state of a single model
 * from the perspective of the authenticated user.
 */
public record UserModelStatusDto(
        String modelId,
        boolean adminEnabled,
        boolean userEnabled,
        boolean effectivelyEnabled
) {}

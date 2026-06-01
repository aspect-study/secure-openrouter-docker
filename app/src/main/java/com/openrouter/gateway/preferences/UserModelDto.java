package com.openrouter.gateway.preferences;

/**
 * Represents a single model entry in the user's effective model list.
 * <p>
 * {@code id} is the {@code model_config} integer PK. The frontend must use this value
 * for all toggle and status API calls ({@code PUT /api/user/models/{id}/toggle}).
 * The {@code modelId} string is for display only and must NEVER be used as a URL path segment
 * (model IDs contain forward slashes that Tomcat normalises before Spring MVC sees the request).
 */
public record UserModelDto(
        Long id,
        String modelId,
        String name,
        boolean adminEnabled,
        boolean userEnabled,
        boolean effectivelyEnabled
) {}

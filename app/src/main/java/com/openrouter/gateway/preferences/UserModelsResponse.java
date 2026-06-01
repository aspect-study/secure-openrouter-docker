package com.openrouter.gateway.preferences;

import java.util.List;

/**
 * Response envelope for {@code GET /api/user/models}.
 * <p>
 * {@code totalUserEnabled} is the count of models that are effectively visible to the user
 * (admin-enabled ∩ user-enabled, accounting for sparse-row defaults). This is NOT the count
 * of explicit {@code enabled = true} rows in the database — rows are only written on toggle,
 * so most "enabled" states have no DB row. The count is derived from the filtered model list.
 * <p>
 * UI display: "Showing {totalUserEnabled} of {totalAdminEnabled} admin-enabled models."
 */
public record UserModelsResponse(
        List<UserModelDto> models,
        int totalAdminEnabled,
        int totalUserEnabled
) {}

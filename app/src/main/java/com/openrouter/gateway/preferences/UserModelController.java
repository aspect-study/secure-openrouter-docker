package com.openrouter.gateway.preferences;

import com.openrouter.gateway.auth.User;
import com.openrouter.gateway.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for user-level model preferences.
 * <p>
 * All three endpoints resolve {@code userId} from the JWT principal (email → DB lookup).
 * No endpoint accepts a {@code userId} as a path or query parameter — this prevents
 * users from reading or modifying each other's preferences.
 * <p>
 * Path variable {@code {id}} is always the {@code model_config} integer PK, never the
 * model ID string. Model IDs (e.g., "meta-llama/llama-3.3-70b-instruct:free") contain
 * forward slashes that Tomcat normalises before Spring MVC sees the request — URL-encoding
 * does not reliably solve this without enabling {@code ALLOW_ENCODED_SLASH}, a known
 * path-traversal risk rejected by Spring Security.
 */
@RestController
@RequestMapping("/api/user/models")
public class UserModelController {

    private static final Logger log = LoggerFactory.getLogger(UserModelController.class);

    private final UserModelPreferenceService preferenceService;
    private final UserRepository userRepository;

    public UserModelController(UserModelPreferenceService preferenceService,
                               UserRepository userRepository) {
        this.preferenceService = preferenceService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/user/models
     * <p>
     * Returns the full model list annotated with admin and user state.
     * Includes both admin-enabled and admin-disabled models so the My Models UI can
     * display dimmed rows for admin-disabled entries.
     * <p>
     * ROLE_ADMIN callers receive all globally-enabled models with userEnabled=true
     * (their saved preference rows, if any, are ignored).
     * <p>
     * Requires ROLE_USER or ROLE_ADMIN (enforced in SecurityConfig).
     */
    @GetMapping
    public ResponseEntity<UserModelsResponse> getEffectiveModels(
            @AuthenticationPrincipal String email) {

        User user = resolveUser(email);
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        UserModelsResponse response = preferenceService.getEffectiveModels(user.getId(), isAdmin);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/user/models/{id}/toggle
     * <p>
     * Atomically flips the user's preference for the model identified by {@code id}
     * (the {@code model_config} integer PK).
     * <p>
     * Returns 400 if the model is admin-disabled — users cannot toggle admin-gated models.
     * Returns 404 if no model_config row exists for the given id.
     * <p>
     * Requires ROLE_USER or ROLE_ADMIN (enforced in SecurityConfig).
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<UserModelStatusDto> toggleModel(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {

        User user = resolveUser(email);
        log.debug("toggleModel: userId={} modelConfigId={}", user.getId(), id);

        UserModelStatusDto result = preferenceService.toggleModel(user.getId(), id);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/user/models/{id}/status
     * <p>
     * Returns the current preference state for a single model identified by {@code id}
     * (the {@code model_config} integer PK).
     * <p>
     * Returns 404 if no model_config row exists for the given id.
     * <p>
     * Requires ROLE_USER or ROLE_ADMIN (enforced in SecurityConfig).
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<UserModelStatusDto> getModelStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {

        User user = resolveUser(email);
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        UserModelStatusDto result = preferenceService.getModelStatus(user.getId(), id, isAdmin);
        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the authenticated user entity from their JWT email claim.
     * Throws IllegalStateException if the user is not found — this should never happen
     * because the JWT filter has already validated the token, but guards against DB inconsistency.
     */
    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found in DB: " + email));
    }
}

package com.openrouter.gateway.preferences;

import com.openrouter.gateway.config.ModelConfig;
import com.openrouter.gateway.config.ModelConfigRepository;
import com.openrouter.gateway.exception.ModelAdminDisabledException;
import com.openrouter.gateway.exception.ModelNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic for user-level model preferences.
 * <p>
 * Hierarchy: Admin gates, user filters.
 * <ul>
 *   <li>Admin controls the global allowlist — a model disabled by admin is unavailable
 *       to everyone, regardless of user preferences.</li>
 *   <li>Users can only filter down from the admin-enabled allowlist — they cannot re-enable
 *       a globally-disabled model.</li>
 *   <li>ROLE_ADMIN callers bypass user preferences entirely — they always see all
 *       globally-enabled models.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class UserModelPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserModelPreferenceService.class);

    private final ModelConfigRepository modelConfigRepository;
    private final UserModelPreferenceRepository preferenceRepository;

    public UserModelPreferenceService(
            ModelConfigRepository modelConfigRepository,
            UserModelPreferenceRepository preferenceRepository) {
        this.modelConfigRepository = modelConfigRepository;
        this.preferenceRepository = preferenceRepository;
    }

    // ── getEffectiveModels ────────────────────────────────────────────────────

    /**
     * Returns the model list visible to the user in their Playground.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Load all globally-enabled models from model_config (admin allowlist).</li>
     *   <li>If caller is ROLE_ADMIN → short-circuit and return all admin-enabled models
     *       with userEnabled=true and effectivelyEnabled=true (preferences ignored).</li>
     *   <li>Load the user's preference rows and build a modelId → enabled map.</li>
     *   <li>Absent rows default to enabled=true (sparse-row semantics).</li>
     *   <li>Return all admin-enabled models, each annotated with its user-level state.
     *       The response includes admin-disabled models as well so the My Models page
     *       can show them dimmed — see UserModelsResponse for counter semantics.</li>
     * </ol>
     * <p>
     * Note: this method returns ALL models from model_config (both enabled and disabled)
     * so the My Models UI can display admin-disabled rows as dimmed. The frontend is
     * responsible for filtering to effectivelyEnabled=true for the Playground dropdown.
     *
     * @param userId  the authenticated user's DB PK
     * @param isAdmin true if caller has ROLE_ADMIN
     */
    public UserModelsResponse getEffectiveModels(Long userId, boolean isAdmin) {
        // All models (enabled and disabled) — My Models page needs both
        List<ModelConfig> allModels = modelConfigRepository.findAllByOrderByModelIdAsc();
        List<ModelConfig> adminEnabled = allModels.stream()
                .filter(ModelConfig::isEnabled)
                .toList();

        if (isAdmin) {
            // Admins always see the full admin-enabled list; preferences are irrelevant
            List<UserModelDto> dtos = allModels.stream()
                    .map(mc -> new UserModelDto(
                            mc.getId(),
                            mc.getModelId(),
                            formatDisplayName(mc.getModelId()),
                            mc.isEnabled(),
                            true,           // userEnabled always true for admins
                            mc.isEnabled()  // effectivelyEnabled = adminEnabled for admins
                    ))
                    .toList();

            int totalAdminEnabled = adminEnabled.size();
            int totalUserEnabled  = totalAdminEnabled; // admins see everything admin-enabled
            log.debug("getEffectiveModels: ROLE_ADMIN shortcircuit — {} admin-enabled models", totalAdminEnabled);
            return new UserModelsResponse(dtos, totalAdminEnabled, totalUserEnabled);
        }

        // Build preference map: modelId → userEnabled (absent = true)
        Map<String, Boolean> prefMap = preferenceRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserModelPreference::getModelId,
                        UserModelPreference::isEnabled
                ));

        List<UserModelDto> dtos = allModels.stream()
                .map(mc -> {
                    boolean adminEnabledFlag = mc.isEnabled();
                    // Absent row → user has never toggled → treat as enabled
                    boolean userEnabledFlag  = prefMap.getOrDefault(mc.getModelId(), true);
                    boolean effective        = adminEnabledFlag && userEnabledFlag;
                    return new UserModelDto(
                            mc.getId(),
                            mc.getModelId(),
                            formatDisplayName(mc.getModelId()),
                            adminEnabledFlag,
                            userEnabledFlag,
                            effective
                    );
                })
                .toList();

        int totalAdminEnabled = adminEnabled.size();
        // effectivelyEnabled count = what the user can actually use
        int totalUserEnabled  = (int) dtos.stream().filter(UserModelDto::effectivelyEnabled).count();

        log.debug("getEffectiveModels: userId={} — {}/{} models effectively enabled",
                userId, totalUserEnabled, totalAdminEnabled);
        return new UserModelsResponse(dtos, totalAdminEnabled, totalUserEnabled);
    }

    // ── toggleModel ───────────────────────────────────────────────────────────

    /**
     * Atomically flips the user's preference for the given model.
     * <p>
     * Guards:
     * <ul>
     *   <li>ModelNotFoundException (404) if modelConfigId does not exist.</li>
     *   <li>ModelAdminDisabledException (400) if the model is admin-disabled —
     *       users cannot toggle admin-disabled models.</li>
     * </ul>
     * <p>
     * Uses {@link UserModelPreferenceRepository#upsertToggle} (INSERT ... ON DUPLICATE KEY UPDATE)
     * rather than load-or-create. This is intentional — see repository Javadoc for the
     * race condition explanation.
     * <p>
     * Re-fetch after upsert reads from primary (single-node setup). If read replicas are
     * added in future, ensure this re-fetch routes to primary to avoid stale reads.
     *
     * @param userId        the authenticated user's DB PK
     * @param modelConfigId the model_config integer PK from the URL path variable
     */
    @Transactional
    public UserModelStatusDto toggleModel(Long userId, Long modelConfigId) {
        ModelConfig modelConfig = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> new ModelNotFoundException(modelConfigId));

        if (!modelConfig.isEnabled()) {
            throw new ModelAdminDisabledException(modelConfig.getModelId());
        }

        // Atomic upsert — never use load-or-create here (race condition risk)
        preferenceRepository.upsertToggle(userId, modelConfig.getModelId());

        // Re-fetch to return the current post-toggle state
        boolean userEnabled = preferenceRepository
                .findByUserIdAndModelId(userId, modelConfig.getModelId())
                .map(UserModelPreference::isEnabled)
                .orElse(true); // should always be present after upsert, but default to true

        boolean effectivelyEnabled = modelConfig.isEnabled() && userEnabled;

        log.info("toggleModel: userId={} modelId={} → userEnabled={} effectivelyEnabled={}",
                userId, modelConfig.getModelId(), userEnabled, effectivelyEnabled);

        return new UserModelStatusDto(
                modelConfig.getModelId(),
                modelConfig.isEnabled(),
                userEnabled,
                effectivelyEnabled
        );
    }

    // ── getModelStatus ────────────────────────────────────────────────────────

    /**
     * Returns the current preference state for a single model.
     *
     * @param userId        the authenticated user's DB PK
     * @param modelConfigId the model_config integer PK from the URL path variable
     * @param isAdmin       true if caller has ROLE_ADMIN (userEnabled always true)
     */
    public UserModelStatusDto getModelStatus(Long userId, Long modelConfigId, boolean isAdmin) {
        ModelConfig modelConfig = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> new ModelNotFoundException(modelConfigId));

        boolean adminEnabled = modelConfig.isEnabled();
        boolean userEnabled;

        if (isAdmin) {
            // Admins always see all admin-enabled models; their preference rows are ignored
            userEnabled = true;
        } else {
            userEnabled = preferenceRepository
                    .findByUserIdAndModelId(userId, modelConfig.getModelId())
                    .map(UserModelPreference::isEnabled)
                    .orElse(true); // absent row = enabled by default
        }

        boolean effectivelyEnabled = adminEnabled && userEnabled;

        return new UserModelStatusDto(
                modelConfig.getModelId(),
                adminEnabled,
                userEnabled,
                effectivelyEnabled
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Derives a human-readable display name from the model ID string.
     * <p>
     * Example: "meta-llama/llama-3.3-70b-instruct:free" → "Llama 3.3 70B Instruct"
     * <p>
     * Strategy: take the part after the last "/" (provider prefix), strip the ":free" suffix,
     * replace hyphens with spaces, and title-case each word.
     * This is best-effort — no external registry lookup is performed.
     */
    static String formatDisplayName(String modelId) {
        if (modelId == null || modelId.isBlank()) return modelId;

        // Strip qualifier suffix (e.g., ":free", ":nitro")
        String stripped = modelId.contains(":") ? modelId.substring(0, modelId.lastIndexOf(':')) : modelId;

        // Take the local name after the provider prefix (e.g., "meta-llama/llama-3.3-70b-instruct")
        String local = stripped.contains("/") ? stripped.substring(stripped.lastIndexOf('/') + 1) : stripped;

        // Convert hyphens to spaces and title-case
        String[] parts = local.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toUpperCase().equals(part.substring(1))
                        ? part.substring(1)                  // already all-caps (e.g. "70B") — keep as-is
                        : part.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}

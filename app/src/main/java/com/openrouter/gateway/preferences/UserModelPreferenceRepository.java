package com.openrouter.gateway.preferences;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for user model preference rows.
 * <p>
 * The critical method here is {@link #upsertToggle}, which performs an atomic
 * INSERT ... ON DUPLICATE KEY UPDATE. This avoids the load-or-create race condition
 * that would occur under concurrent toggle requests (two concurrent INSERTs would violate
 * the unique constraint, two concurrent reads of the same absent row would both insert,
 * last-write-wins flip produces wrong state). The native query handles both cases atomically.
 * <p>
 * Sparse-row semantics: absence of a row = model is enabled for the user (default).
 * The first explicit toggle always means "turn off" — the INSERT inserts {@code enabled = b'0'}.
 * Subsequent toggles flip the existing row via ON DUPLICATE KEY UPDATE.
 */
public interface UserModelPreferenceRepository extends JpaRepository<UserModelPreference, Long> {

    /**
     * Finds a user's preference for a specific model by the string model ID.
     * Returns empty if the user has never toggled this model (treat as enabled).
     */
    Optional<UserModelPreference> findByUserIdAndModelId(Long userId, String modelId);

    /**
     * Returns all preference rows for a user. Does not include models the user
     * has never explicitly toggled (those are absent = enabled by default).
     */
    List<UserModelPreference> findByUserId(Long userId);

    /**
     * Removes the preference row for a user/model pair.
     * After deletion the model reverts to the default-enabled state.
     */
    @Modifying
    @Transactional
    void deleteByUserIdAndModelId(Long userId, String modelId);

    /**
     * Atomically toggles the user's preference for the given model.
     * <p>
     * Behavior:
     * <ul>
     *   <li>If no row exists: inserts with {@code enabled = b'0'} (first toggle = disable).</li>
     *   <li>If a row exists: flips {@code enabled} via {@code NOT enabled}.</li>
     * </ul>
     * <p>
     * This native INSERT ... ON DUPLICATE KEY UPDATE is intentionally atomic — do NOT
     * replace this with a load-or-create pattern. See class-level Javadoc for the race condition.
     *
     * @param userId  the user's database PK
     * @param modelId the model_config.model_id string (not the integer PK)
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_model_preferences (user_id, model_id, enabled)
        VALUES (:userId, :modelId, b'0')
        ON DUPLICATE KEY UPDATE enabled = NOT enabled
        """, nativeQuery = true)
    void upsertToggle(@Param("userId") Long userId, @Param("modelId") String modelId);
}

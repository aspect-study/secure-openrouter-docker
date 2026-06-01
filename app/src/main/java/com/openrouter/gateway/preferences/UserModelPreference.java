package com.openrouter.gateway.preferences;

import com.openrouter.gateway.auth.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Stores a user's explicit enable/disable preference for a model.
 * <p>
 * Design invariants:
 * <ul>
 *   <li>Sparse table — a row is only written when a user explicitly toggles a model.
 *       Absence of a row means "enabled" (default state).</li>
 *   <li>{@code enabled = true} → user wants the model visible in their Playground.</li>
 *   <li>{@code enabled = false} → user has hidden this model.</li>
 *   <li>{@code updated_at} is managed entirely by MySQL via ON UPDATE CURRENT_TIMESTAMP.
 *       The application must never write this column — hence {@code insertable = false, updatable = false}.</li>
 *   <li>No FK constraint on {@code model_id}: model_config rows can be removed without
 *       cascading deletes. Orphaned rows are silently excluded by getEffectiveModels JOIN.</li>
 * </ul>
 */
@Entity
@Table(
    name = "user_model_preferences",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_model_pref",
        columnNames = {"user_id", "model_id"}
    )
)
public class UserModelPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The owning user. Lazy-loaded — most service calls only need userId.
     * ON DELETE CASCADE is enforced at DB level (see V5 migration).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The OpenRouter model ID string (e.g., "meta-llama/llama-3.3-70b-instruct:free").
     * Matches model_config.model_id — no FK constraint by design.
     */
    @Column(name = "model_id", nullable = false, length = 150)
    private String modelId;

    /**
     * Whether the user has this model enabled in their Playground.
     * Default true — but rows are only inserted on explicit toggle, so this field
     * starts as false on first INSERT (upsertToggle inserts as disabled, then flips).
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * DB-managed timestamp — do NOT set in application code.
     * MySQL updates this automatically via ON UPDATE CURRENT_TIMESTAMP.
     */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public UserModelPreference() {}

    public UserModelPreference(User user, String modelId, boolean enabled) {
        this.user = user;
        this.modelId = modelId;
        this.enabled = enabled;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

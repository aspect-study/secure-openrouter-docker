package com.openrouter.gateway.usage;

import com.openrouter.gateway.auth.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Admin-controlled daily usage limits per model.
 *
 * A row with user = null is the global default for that model.
 * A row with a specific user overrides the global default for that user on that model.
 * Querying for a user's effective limit: user-specific row first, fall back to global (user IS NULL).
 */
@Entity
@Table(
    name = "model_usage_limits",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_model_usage_limits",
        columnNames = {"model_id", "user_id"}
    )
)
public class ModelUsageLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false, length = 150)
    private String modelId;

    // null = global default for this model
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "max_requests_per_day", nullable = false)
    private int maxRequestsPerDay = 50;

    @Column(name = "max_tokens_per_day", nullable = false)
    private int maxTokensPerDay = 100_000;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public ModelUsageLimit() {}

    public ModelUsageLimit(String modelId, User user, int maxRequestsPerDay, int maxTokensPerDay) {
        this.modelId = modelId;
        this.user = user;
        this.maxRequestsPerDay = maxRequestsPerDay;
        this.maxTokensPerDay = maxTokensPerDay;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getMaxRequestsPerDay() { return maxRequestsPerDay; }
    public void setMaxRequestsPerDay(int maxRequestsPerDay) { this.maxRequestsPerDay = maxRequestsPerDay; }

    public int getMaxTokensPerDay() { return maxTokensPerDay; }
    public void setMaxTokensPerDay(int maxTokensPerDay) { this.maxTokensPerDay = maxTokensPerDay; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

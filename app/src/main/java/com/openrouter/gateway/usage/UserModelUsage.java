package com.openrouter.gateway.usage;

import com.openrouter.gateway.auth.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily per-user-per-model usage counters.
 *
 * One row per (user, model, period_date). Period dates are always UTC.
 * No scheduled reset job — a new day creates a new row automatically.
 *
 * reset_at is stored (= midnight UTC of the next day) so the UI can show
 * "resets in 6h 23m" without any client-side date arithmetic.
 */
@Entity
@Table(
    name = "user_model_usage",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_model_usage",
        columnNames = {"user_id", "model_id", "period_date"}
    )
)
public class UserModelUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "model_id", nullable = false, length = 150)
    private String modelId;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "request_count", nullable = false)
    private int requestCount = 0;

    @Column(name = "token_count", nullable = false)
    private int tokenCount = 0;

    /** Midnight UTC of the next day — stored for easy "resets in X" display. */
    @Column(name = "reset_at", nullable = false)
    private LocalDateTime resetAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public UserModelUsage() {}

    public UserModelUsage(User user, String modelId, LocalDate periodDate, LocalDateTime resetAt) {
        this.user = user;
        this.modelId = modelId;
        this.periodDate = periodDate;
        this.resetAt = resetAt;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public LocalDate getPeriodDate() { return periodDate; }
    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }

    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }

    public LocalDateTime getResetAt() { return resetAt; }
    public void setResetAt(LocalDateTime resetAt) { this.resetAt = resetAt; }

    // ── Helpers ───────────────────────────────────────────────────────────

    public void incrementRequests() { this.requestCount++; }

    public void addTokens(int tokens) { this.tokenCount += tokens; }
}

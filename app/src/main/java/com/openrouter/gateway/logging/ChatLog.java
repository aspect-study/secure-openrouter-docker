package com.openrouter.gateway.logging;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persists every chat request/response to MySQL.
 * Used for usage tracking and debugging.
 */
@Entity
@Table(name = "chat_logs", indexes = {
        @Index(name = "idx_chat_logs_user_email", columnList = "userEmail"),
        @Index(name = "idx_chat_logs_created_at", columnList = "createdAt")
})
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String userEmail;

    @Column(nullable = false, length = 100)
    private String model;

    // Tokens and cost from OpenRouter usage object
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    // Latency in milliseconds
    private long latencyMs;

    // HTTP status returned by OpenRouter
    private int statusCode;

    // First 500 chars of the assistant response (for quick inspection)
    @Column(length = 500)
    private String responsePreview;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public ChatLog() {}

    // ── Builder-style static factory ──────────────────────────────────────

    public static ChatLog of(String userEmail, String model, int promptTokens,
                              int completionTokens, int totalTokens,
                              long latencyMs, int statusCode, String responsePreview) {
        ChatLog log = new ChatLog();
        log.userEmail = userEmail;
        log.model = model;
        log.promptTokens = promptTokens;
        log.completionTokens = completionTokens;
        log.totalTokens = totalTokens;
        log.latencyMs = latencyMs;
        log.statusCode = statusCode;
        log.responsePreview = responsePreview != null
                ? responsePreview.substring(0, Math.min(responsePreview.length(), 500))
                : null;
        return log;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public long getLatencyMs() { return latencyMs; }
    public int getStatusCode() { return statusCode; }
    public String getResponsePreview() { return responsePreview; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

-- ============================================================
-- V4__byok_usage_tracking.sql
-- Adds per-user OpenRouter API key storage and daily usage tracking.
--
-- NEVER EDIT this file after it has been applied to any environment.
-- Add V5+ for future changes.
--
-- Boolean columns use BIT(1) — required by Hibernate 6 / MySQL8Dialect.
-- ============================================================

-- ── 1. Add BYOK columns to users ─────────────────────────────
ALTER TABLE users
    ADD COLUMN openrouter_key_encrypted VARCHAR(512) NULL,
    ADD COLUMN openrouter_key_validated  BIT(1)       NOT NULL DEFAULT b'0',
    ADD COLUMN openrouter_key_set_at     DATETIME(6)  NULL;

-- ── 2. model_usage_limits (admin-controlled per-model limits) ─
-- A row with user_id = NULL is the global default for that model.
-- A row with a specific user_id overrides the global default for that user.
CREATE TABLE IF NOT EXISTS model_usage_limits (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    model_id             VARCHAR(150) NOT NULL,
    user_id              BIGINT       NULL,
    max_requests_per_day INT          NOT NULL DEFAULT 50,
    max_tokens_per_day   INT          NOT NULL DEFAULT 100000,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_usage_limits (model_id, user_id),
    CONSTRAINT fk_mul_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── 3. user_model_usage (daily per-user-per-model counters) ───
-- One row per user per model per UTC day.
-- No scheduled reset job needed — a new row starts a new window.
-- reset_at = midnight UTC of next day, stored for "resets in X" display.
CREATE TABLE IF NOT EXISTS user_model_usage (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    model_id      VARCHAR(150) NOT NULL,
    period_date   DATE         NOT NULL,
    request_count INT          NOT NULL DEFAULT 0,
    token_count   INT          NOT NULL DEFAULT 0,
    reset_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_model_usage (user_id, model_id, period_date),
    INDEX idx_umu_user_date (user_id, period_date),
    CONSTRAINT fk_umu_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

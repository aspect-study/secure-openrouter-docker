-- ============================================================
-- V1__initial_schema.sql
-- Creates all tables from scratch.
-- Column types match what Hibernate 6 / MySQL8Dialect generates:
--   boolean  → TINYINT  (no display width — deprecated in MySQL 8.0.17+)
--   LocalDateTime with @CreationTimestamp/@UpdateTimestamp → DATETIME(6)
--   ENUM via @Enumerated(STRING) → VARCHAR(n)
-- ============================================================

-- ── users ────────────────────────────────────────────────────
-- Managed by: User.java
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(60)  NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active        TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── chat_logs ─────────────────────────────────────────────────
-- Managed by: ChatLog.java
CREATE TABLE IF NOT EXISTS chat_logs (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_email        VARCHAR(150) NOT NULL,
    model             VARCHAR(100) NOT NULL,
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    total_tokens      INT          NOT NULL DEFAULT 0,
    latency_ms        BIGINT       NOT NULL DEFAULT 0,
    status_code       INT          NOT NULL DEFAULT 0,
    response_preview  VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_logs_user_email (user_email),
    INDEX idx_chat_logs_created_at (created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── model_config ──────────────────────────────────────────────
-- Managed by: ModelConfig.java
-- Seeded in V2.
-- created_at has DEFAULT CURRENT_TIMESTAMP(6) so V2's INSERT IGNORE
-- does not need to supply it (Hibernate @CreationTimestamp only fires
-- from the app layer, not from raw SQL migrations).
CREATE TABLE IF NOT EXISTS model_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    model_id     VARCHAR(150) NOT NULL,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    last_used_at DATETIME(6),
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_model_config_model_id UNIQUE (model_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── conversations ─────────────────────────────────────────────
-- Managed by: Conversation.java
CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_email VARCHAR(150) NOT NULL,
    title      VARCHAR(255) NOT NULL DEFAULT 'New Conversation',
    model      VARCHAR(150) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_conversations_user_email (user_email),
    INDEX idx_conversations_updated_at (updated_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── conversation_messages ─────────────────────────────────────
-- Managed by: ConversationMessage.java
CREATE TABLE IF NOT EXISTS conversation_messages (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         MEDIUMTEXT  NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    INDEX idx_messages_conversation_id (conversation_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

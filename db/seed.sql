-- ============================================================
-- DEPRECATED — DO NOT USE
-- Schema and seed data are now managed by Flyway.
-- See: app/src/main/resources/db/migration/
--   V1__initial_schema.sql  — all CREATE TABLE statements
--   V2__seed_model_config.sql — model_config rows
--   V3__seed_admin_user.sql   — admin user
--
-- This file is kept for historical reference only.
-- It is NOT mounted into the MySQL container (see docker-compose.yml).
-- ============================================================

USE openrouter_gateway;

-- ── Admin user ───────────────────────────────────────────────
-- Password: Admin@2026! (BCrypt strength 12)
-- Change this password immediately after first login.
INSERT IGNORE INTO users (email, password_hash, role, created_at, updated_at)
VALUES (
    'admin@openrouter.local',
    '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
    'ADMIN',
    NOW(),
    NOW()
);

-- ── model_config table ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS model_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_id    VARCHAR(150) NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at DATETIME NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_model_config_model_id UNIQUE (model_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Seed allowed free models
INSERT IGNORE INTO model_config (model_id, enabled) VALUES
    ('nvidia/nemotron-nano-9b-v2:free', TRUE),
    ('nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free', TRUE),
    ('nvidia/nemotron-3-super-120b-a12b:free', TRUE),
    ('nvidia/nemotron-3-nano-30b-a3b:free', TRUE),
    ('nvidia/nemotron-nano-12b-v2-vl:free', TRUE),
    ('meta-llama/llama-3.3-70b-instruct:free', TRUE),
    ('meta-llama/llama-3.2-3b-instruct:free', TRUE),
    ('deepseek/deepseek-v4-flash:free', TRUE),
    ('qwen/qwen3-coder:free', TRUE),
    ('qwen/qwen3-next-80b-a3b-instruct:free', TRUE),
    ('google/gemma-4-31b-it:free', TRUE),
    ('google/gemma-4-26b-a4b-it:free', TRUE),
    ('openai/gpt-oss-120b:free', TRUE),
    ('openai/gpt-oss-20b:free', TRUE),
    ('poolside/laguna-xs.2:free', TRUE),
    ('poolside/laguna-m.1:free', TRUE),
    ('liquid/lfm-2.5-1.2b-thinking:free', TRUE),
    ('liquid/lfm-2.5-1.2b-instruct:free', TRUE),
    ('moonshotai/kimi-k2.6:free', TRUE),
    ('z-ai/glm-4.5-air:free', TRUE),
    ('cognitivecomputations/dolphin-mistral-24b-venice-edition:free', TRUE),
    ('nousresearch/hermes-3-llama-3.1-405b:free', TRUE),
    ('openrouter/owl-alpha', TRUE),
    ('openrouter/free', TRUE);

-- ── conversations table ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email  VARCHAR(150) NOT NULL,
    title       VARCHAR(255) NOT NULL DEFAULT 'New Conversation',
    model       VARCHAR(150) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conversations_user_email (user_email),
    INDEX idx_conversations_updated_at (updated_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── conversation_messages table ──────────────────────────────
CREATE TABLE IF NOT EXISTS conversation_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role            ENUM('user', 'assistant') NOT NULL,
    content         MEDIUMTEXT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
        ON DELETE CASCADE,
    INDEX idx_messages_conversation_id (conversation_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

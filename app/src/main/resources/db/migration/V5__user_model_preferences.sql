-- V5: user_model_preferences table
-- Stores per-user model toggle state. Sparse by design — absence of a row means enabled (default).
-- BIT(1) required: Hibernate 6 / MySQL8Dialect maps Java boolean → BIT; TINYINT causes SchemaManagementException.
-- No FK on model_id: model_config rows can be removed without cascading; orphaned rows are harmless
-- (getEffectiveModels filters via JOIN on model_config, so orphaned rows are silently excluded).
-- updated_at is DB-managed via ON UPDATE CURRENT_TIMESTAMP — application must NOT write this column.

CREATE TABLE user_model_preferences (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  model_id   VARCHAR(150) NOT NULL,
  enabled    BIT(1)       NOT NULL DEFAULT b'1',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_model_pref (user_id, model_id),
  CONSTRAINT fk_ump_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

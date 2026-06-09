# Database Schema

Schema owned by Flyway (`ddl-auto=validate`). Never change back to `update` or `create`.

## Tables

| Table | Description |
|---|---|
| `users` | email, password_hash (BCrypt), role (USER/ADMIN), active, timestamps; V4 adds: openrouter_key_encrypted (AES-GCM), openrouter_key_validated (BIT), openrouter_key_set_at |
| `chat_logs` | Per-request log: user, model, tokens, latency, status, response preview |
| `model_config` | Enabled/disabled state per free model; V2 seeds rows with `enabled=true`; `FreeModelSyncService` inserts new rows with `enabled=false` |
| `conversations` | Per-user chat sessions: title, model, timestamps |
| `conversation_messages` | Messages per conversation: role (user/assistant), content |
| `model_usage_limits` | V4: Admin-controlled daily limits per model; `user_id=NULL` = global default |
| `user_model_usage` | V4: Daily per-user-per-model counters (request_count, token_count, reset_at) |
| `user_model_preferences` | V5: Per-user model toggle state (sparse — absence of row = enabled by default) |

## Flyway Migrations

| Version | File | Description |
|---|---|---|
| V1 | `V1__initial_schema.sql` | All 5 core tables |
| V2 | `V2__seed_model_config.sql` | Initial free model rows |
| V3 | `V3__seed_admin_user.sql` | Default admin user |
| V4 | `V4__byok_usage_tracking.sql` | BYOK + usage tables |
| V5 | `V5__user_model_preferences.sql` | Per-user model toggle table |
| V6 | `V6__disable_removed_free_models.sql` | Disable models removed from free tier |
| V7 | `V7__cleanup_free_model_duplicates.sql` | Remove base IDs that have a `:free` counterpart |

**Next migration:** V8 — V1–V7 are locked; never edit applied migrations.

## Key Rules

- **Boolean columns must be `BIT(1)` not `TINYINT`** — Hibernate 6 / MySQL8Dialect maps Java `boolean` → `BIT`; `TINYINT` causes `SchemaManagementException` on startup
- **`model_usage_limits` global row uses `user_id = NULL`** — always query with `IS NULL`, never `= NULL`
- **`user_model_preferences.updated_at` is DB-managed** — uses `ON UPDATE CURRENT_TIMESTAMP`; entity field is `insertable=false, updatable=false` — never set in application code
- **`user_model_preferences` is sparse** — absence of a row means enabled; do not pre-populate for every user/model combination
- **No FK on `user_model_preferences.model_id`** — orphaned rows from removed `model_config` entries are harmless; `getEffectiveModels` excludes them via JOIN
- **`@Transactional` required on controller methods accessing lazy collections** — Spring closes the Hibernate session after each repository call; add `@Transactional(readOnly = true)` to GET methods, `@Transactional` to write methods in `ConversationController`
- **No usage reset scheduled job** — usage windows are date-keyed; a new UTC day creates a fresh row automatically
- **`toggleModel` must use atomic upsert** — `INSERT ... ON DUPLICATE KEY UPDATE enabled = NOT enabled` against `user_model_preferences`; load-or-create is forbidden (concurrent INSERTs violate the unique key or produce wrong state via last-write-wins)
- **Request limit is pre-call, token limit is post-call** — request count is checked and incremented before forwarding (tokens are unknown); token count is checked after the response; if over limit, the next call is hard-blocked

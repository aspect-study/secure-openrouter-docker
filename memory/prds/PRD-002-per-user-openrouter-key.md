# PRD-002 — Per-User OpenRouter API Key (BYOK)

**Status:** PENDING — not yet implemented  
**Created:** 2026-05-31  
**Updated:** 2026-05-31 — added per-model usage tracking feature  
**Author:** aspect  

---

## Problem Statement

The current system uses a single shared OpenRouter API key (the owner's) for all user chat
requests. This creates two problems:

1. The owner's key absorbs all usage, rate limits, and abuse risk from every user.
2. If the system is opened publicly, any user can exhaust the owner's OpenRouter quota.

---

## Decision: Bring Your Own Key (BYOK) with Clear Value Proposition

Users supply their own OpenRouter API key during onboarding (after registration).
The system stores it encrypted and uses it exclusively for that user's chat requests.
The owner's key is never used for user-initiated chat.

### Why BYOK over Provisioned Sub-Keys

- **Provisioned sub-keys** (OpenRouter Management API) create keys under the owner's account.
  All sub-key usage still ties back to the owner's account — not true isolation.
  Also adds significant complexity: provisioning lifecycle, retry scheduler, FAILED states,
  background jobs, Management API dependency.

- **BYOK** gives users a genuinely independent key tied to their own OpenRouter account.
  Rate limits, abuse flags, and quota are entirely theirs — completely isolated from the owner.

### Why Users Will Accept It

The value proposition is explicit and fair:

> "Connect your free OpenRouter API key and get access to 24+ free LLM models through
> a clean interface with conversation history, model switching, and an admin dashboard —
> all at no cost."

Users understand exactly why the key is needed and benefit directly from providing it.
This is meaningfully different from opaque "give us your key" requests — the use case
is transparent and the benefit is immediate.

OpenRouter accounts are free and take under 2 minutes to create.

---

## User Flow

```
1. Register (email + password)  — unchanged
2. Login                        — unchanged
3. Redirected to Settings page  — new
4. "Add your OpenRouter API key to start chatting"
5. User pastes key → system validates it live against OpenRouter
6. Key saved encrypted → Playground unlocked
```

If a user tries to chat without a saved key:
```json
{ "error": "Please add your OpenRouter API key in Settings to start chatting." }
```

---

## Scope

### In Scope

- Store user's OpenRouter key encrypted (AES-GCM) in the `users` table
- Validate the key on save (live call to OpenRouter `/api/v1/auth/key`)
- Use per-user key for all chat requests (ChatService + ConversationService)
- nginx pass-through: forward Authorization header as-is instead of injecting owner's key
- Settings page in admin-ui: add/update/remove API key
- Clear UX prompt when no key is saved
- Admin visibility: which users have a key configured (boolean, not the key itself)

### Out of Scope

- OpenRouter Management API / provisioned sub-keys
- Viewing or managing other users' keys (admin sees configured: yes/no only)
- Key rotation automation
- Cross-period historical analytics (this tracks current window only)

---

## Schema Changes

Single Flyway migration (V4) — three tables:

### Table 1: `users` additions

```sql
ALTER TABLE users
  ADD COLUMN openrouter_key_encrypted VARCHAR(512) NULL,
  ADD COLUMN openrouter_key_validated  BIT(1)       NOT NULL DEFAULT b'0',
  ADD COLUMN openrouter_key_set_at     DATETIME     NULL;
```

- `openrouter_key_encrypted` — AES-GCM encrypted plaintext key, decrypted at read time
- `openrouter_key_validated` — TRUE once successfully validated against OpenRouter
- `openrouter_key_set_at`    — timestamp of last key save (audit trail)

### Table 2: `model_usage_limits` (admin-controlled limits)

```sql
CREATE TABLE model_usage_limits (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  model_id            VARCHAR(150)  NOT NULL,
  user_id             BIGINT        NULL,           -- NULL = global default for this model
  max_requests_per_day INT          NOT NULL DEFAULT 50,
  max_tokens_per_day   INT          NOT NULL DEFAULT 100000,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_model_usage_limits (model_id, user_id),
  CONSTRAINT fk_mul_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

- A row with `user_id = NULL` is the global default for that model.
- A row with a specific `user_id` overrides the global default for that user on that model.
- Admin sets these via admin endpoints.

### Table 3: `user_model_usage` (daily usage counters)

```sql
CREATE TABLE user_model_usage (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  model_id       VARCHAR(150) NOT NULL,
  period_date    DATE         NOT NULL,             -- UTC date of this window
  request_count  INT          NOT NULL DEFAULT 0,
  token_count    INT          NOT NULL DEFAULT 0,
  reset_at       DATETIME     NOT NULL,             -- midnight UTC of period_date + 1 day
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_model_usage (user_id, model_id, period_date),
  INDEX idx_umu_user_date (user_id, period_date),
  CONSTRAINT fk_umu_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

- One row per user per model per day. No scheduled reset job needed — a new row is
  inserted when a new day begins. `period_date` is always UTC.
- `reset_at` = midnight UTC of the next day (computed at insert time, stored for
  easy "resets in X hours" display without client-side math).
- `token_count` = `prompt_tokens + completion_tokens` from the OpenRouter response.

**Note:** `BIT(1)` is required for all boolean columns under Hibernate 6 / MySQL8Dialect +
`ddl-auto=validate`. Using `BOOLEAN` or `TINYINT` causes `SchemaManagementException` at startup.

---

## New Components

### Backend

**`AesEncryptedStringConverter`** (`config` package)
- Implements `AttributeConverter<String, String>`
- Algorithm: AES/GCM/NoPadding, 12-byte random IV, 128-bit tag
- Master key: `${ENCRYPTION_MASTER_KEY}` — 32-byte hex env variable
- Encode: generate IV → encrypt → Base64(IV + ciphertext)
- Decode: Base64 decode → split IV (12 bytes) + ciphertext → decrypt
- Null-safe in both directions
- Declared as `@Component` so Spring manages injection of master key via `@Value`

**`OpenRouterKeyService`** (`auth` package or new `apikey` package)
- `saveKey(String userEmail, String rawKey)`:
  - Validate key: GET `https://openrouter.ai/api/v1/auth/key`
    with `Authorization: Bearer {rawKey}` — expect 200
  - On valid: encrypt and persist, set `openrouterKeyValidated = true`, set `openrouterKeySetAt`
  - On invalid: throw `InvalidApiKeyException` (400 to client)
- `removeKey(String userEmail)`: null out encrypted key, reset validated flag
- `getKeyForUser(String userEmail)`: load user, return decrypted key or throw `KeyNotConfiguredException`

**`ApiKeyController`** (`apikey` package)
```
PUT    /api/user/api-key    body: {"apiKey": "sk-or-v1-..."}  — save + validate
DELETE /api/user/api-key                                       — remove key
GET    /api/user/api-key/status                                — returns {configured: true/false}
```
All endpoints require authenticated user (JWT). User operates on their own key only.

**`InvalidApiKeyException`** → 400 Bad Request  
**`KeyNotConfiguredException`** → 409 Conflict (with message pointing user to settings)  
**`UsageLimitExceededException`** → 429 Too Many Requests (includes `resetAt` in response body)

Add handlers for all three in `GlobalExceptionHandler`.

---

### Usage Tracking — New Components

**Entities**

`ModelUsageLimit` (`usage` package):
- Fields: `id`, `modelId`, `user` (nullable `@ManyToOne`), `maxRequestsPerDay`,
  `maxTokensPerDay`, `createdAt`, `updatedAt`
- `user = null` row = global default for that model

`UserModelUsage` (`usage` package):
- Fields: `id`, `user` (`@ManyToOne`), `modelId`, `periodDate` (LocalDate), `requestCount`,
  `tokenCount`, `resetAt` (LocalDateTime)
- `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","model_id","period_date"}))`

**Repositories**

`ModelUsageLimitRepository`:
```java
Optional<ModelUsageLimit> findByModelIdAndUserIsNull(String modelId);           // global default
Optional<ModelUsageLimit> findByModelIdAndUserId(String modelId, Long userId);  // user override
List<ModelUsageLimit> findByUserIsNull();                                        // all global defaults
List<ModelUsageLimit> findByUserId(Long userId);                                 // all overrides for user
```

`UserModelUsageRepository`:
```java
Optional<UserModelUsage> findByUserIdAndModelIdAndPeriodDate(Long userId, String modelId, LocalDate date);
List<UserModelUsage> findByUserIdAndPeriodDate(Long userId, LocalDate date);     // today's full summary
```

**`UsageTrackingService`** (`usage` package)

```
checkAndIncrementUsage(Long userId, String modelId, int tokensUsed):
  1. Load today's usage row (userId + modelId + today UTC); create if absent
  2. Resolve limit: user override → fall back to global default → fall back to system default
     (system default: 50 requests / 100,000 tokens per day if no DB row exists)
  3. Hard block check BEFORE forwarding:
     if requestCount >= maxRequestsPerDay → throw UsageLimitExceededException("requests", resetAt)
     if tokenCount + tokensUsed > maxTokensPerDay → throw UsageLimitExceededException("tokens", resetAt)
  4. Increment requestCount + tokenCount, save

getUserUsageSummary(Long userId):
  Load all today's usage rows for user → build per-model list + global aggregate

getModelUsage(Long userId, String modelId):
  Load today's row for that model; return zeros if absent
```

Note on token check timing: request count is checked before the call (we don't know token
count yet). Token count is checked after the response — if tokens would push over the limit,
the request is allowed but a soft warning is returned. Hard token block applies on the next
request. This avoids blocking a request mid-flight based on estimated tokens.

Revised flow in `ChatService` / `ConversationService`:
```
1. Check rate limit (Bucket4j)            — existing
2. Resolve user entity + API key          — new (Phase 1)
3. PRE-CHECK: usageTrackingService.checkRequestLimit(userId, modelId)  — new
4. Forward to OpenRouter with user key    — modified
5. Parse token usage from response        — existing
6. POST-CALL: usageTrackingService.incrementUsage(userId, modelId, tokens)  — new
7. Persist chat log                       — existing
```

**`UsageLimitExceededException`**
```java
public class UsageLimitExceededException extends RuntimeException {
    private final String limitType;    // "requests" or "tokens"
    private final LocalDateTime resetAt;
    // constructor + getters
}
```

`GlobalExceptionHandler` maps this to 429 with body:
```json
{
  "error": "Daily request limit reached for model llama-3.3-70b. Resets at 2026-06-01T00:00:00Z.",
  "limitType": "requests",
  "resetAt": "2026-06-01T00:00:00Z"
}
```

**Admin limit management endpoints** (add to `AdminController`):

```
GET  /api/admin/usage/limits                    — all global defaults
PUT  /api/admin/usage/limits/{modelId}          — set/update global default
     body: {"maxRequestsPerDay": 50, "maxTokensPerDay": 100000}

GET  /api/admin/users/{id}/usage/limits         — user's overrides
PUT  /api/admin/users/{id}/usage/limits/{modelId} — set/update user override
DELETE /api/admin/users/{id}/usage/limits/{modelId} — remove override (falls back to global)

GET  /api/admin/users/{id}/usage               — today's usage per model for a user
```

**User-facing usage endpoints** (add to `ApiKeyController` or new `UsageController`):

```
GET  /api/user/usage           — today's usage summary: per-model list + global aggregate
GET  /api/user/usage/{modelId} — today's usage for a specific model
```

Response shape for `/api/user/usage`:
```json
{
  "date": "2026-06-01",
  "resetAt": "2026-06-02T00:00:00Z",
  "globalAggregate": {
    "totalRequests": 23,
    "totalTokens": 45200
  },
  "models": [
    {
      "modelId": "meta-llama/llama-3.3-70b-instruct:free",
      "requests": 10,
      "maxRequests": 50,
      "tokens": 20000,
      "maxTokens": 100000,
      "requestsRemaining": 40,
      "tokensRemaining": 80000,
      "resetAt": "2026-06-02T00:00:00Z",
      "limitSource": "GLOBAL"   // or "USER_OVERRIDE"
    }
  ]
}
```

### ChatService + ConversationService changes

Both services resolve the user entity from `userEmail`, call `openRouterKeyService.getKeyForUser()`,
and pass the plaintext key to `OpenRouterClient`.

`OpenRouterClient` method signatures change:
```java
public ProxyResponse chat(String requestBody, String apiKey)
public void streamChatCompletion(String requestBody, String apiKey, Consumer<String> chunkConsumer)
```

Both set `Authorization: Bearer {apiKey}` in the outgoing `HttpRequest` to nginx.

### nginx.conf change

Replace hardcoded header injection:
```nginx
# Before
proxy_set_header Authorization "Bearer ${OPENROUTER_API_KEY}";

# After
proxy_set_header Authorization $http_authorization;
```

nginx becomes transparent for auth — forwards whatever Spring Boot sets.
The `OPENROUTER_API_KEY` env var remains in `.env` for system-level/health use but is no
longer injected into user chat requests.

### application.properties additions

```properties
encryption.master-key=${ENCRYPTION_MASTER_KEY}
```

### docker-compose.yml addition (openrouter-app environment block)

```yaml
ENCRYPTION_MASTER_KEY: ${ENCRYPTION_MASTER_KEY}
```

### .env.example additions

```bash
# AES-GCM master key for encrypting stored OpenRouter keys
# Generate: openssl rand -hex 32
ENCRYPTION_MASTER_KEY=your-32-byte-hex-here
```

---

## Admin UI Changes

**Settings page** (new page or section in existing user profile area):
- Input field: "Your OpenRouter API Key" (password-type, masked)
- "Get your free key at openrouter.ai/keys" link
- Save button → calls `PUT /api/user/api-key`
- Remove button (if key already set) → calls `DELETE /api/user/api-key`
- Status indicator: "Key configured ✓" or "No key set"
- Validation feedback: "Invalid key — please check and try again"

**Usage Dashboard (tab or section within Settings page):**
- Calls `GET /api/user/usage` on load
- Global aggregate card at top: "Today: 23 requests / 45,200 tokens across all models"
- Per-model table with columns:
  - Model name
  - Requests: progress bar `10 / 50` with percentage
  - Tokens: progress bar `20,000 / 100,000` with percentage
  - Resets in: countdown display e.g. "in 6h 23m" (computed from `resetAt`)
- Color coding: green < 70%, yellow 70–90%, red > 90%
- Refresh button (re-fetches without full page reload)
- If no usage today: "No requests made today. Limits reset daily at midnight UTC."

**Playground page:**
- If `GET /api/user/api-key/status` returns `{configured: false}`:
  show a banner: "Add your OpenRouter API key in Settings to start chatting"
  with a direct link to the settings page
- After each message: show a subtle usage chip below the send button,
  e.g. "llama-3.3-70b: 11/50 requests today" — fetched from `/api/user/usage/{modelId}`
- When 429 is received from backend: show inline error with reset time,
  e.g. "Daily limit reached for this model. Resets in 6h 23m."
- No other changes to the chat flow

**User Manager (admin view):**
- Add `keyConfigured: boolean` and `todayTotalRequests: int` to `UserDto`
- Show key status as a badge in the user table (no key value exposed to admin)
- Expandable row or side panel: today's per-model usage for that user
  (calls `GET /api/admin/users/{id}/usage`)

**Admin Usage Limits page** (new admin page or tab in Model Manager):
- Table of all free models with global default limits (requests/day, tokens/day)
- Inline edit per row: click to edit, save calls `PUT /api/admin/usage/limits/{modelId}`
- Per-user overrides section: select a user, view/edit their model-specific overrides
- "Reset to global default" button per override row

---

## Implementation Order (when ready to build)

```
── Phase A: Infrastructure ──────────────────────────────────────────────────
1.  Flyway V4 migration: users columns + model_usage_limits + user_model_usage
2.  AesEncryptedStringConverter (@Component, @Value master key injection)
3.  User entity: 3 new fields with @Convert annotation on encrypted column
4.  ModelUsageLimit entity + ModelUsageLimitRepository
5.  UserModelUsage entity + UserModelUsageRepository
6.  application.properties + docker-compose + .env.example

── Phase B: Exceptions ──────────────────────────────────────────────────────
7.  InvalidApiKeyException (400)
8.  KeyNotConfiguredException (409)
9.  UsageLimitExceededException (429, carries limitType + resetAt)
10. GlobalExceptionHandler: handlers for all three

── Phase C: API Key Feature ─────────────────────────────────────────────────
11. OpenRouterKeyService (validate + save + remove + get)
12. ApiKeyController: PUT / DELETE / GET /api/user/api-key/*

── Phase D: Usage Tracking ──────────────────────────────────────────────────
13. UsageTrackingService (checkRequestLimit + incrementUsage + getUserUsageSummary)
14. UsageController: GET /api/user/usage, GET /api/user/usage/{modelId}
15. Admin usage endpoints in AdminController (limits CRUD + user usage view)

── Phase E: Chat Flow ───────────────────────────────────────────────────────
16. OpenRouterClient: add apiKey param to chat() and streamChatCompletion()
17. ChatService: resolve user → check key → pre-check usage → call → increment usage
18. ConversationService: same treatment
19. ConversationController: verify non-streaming path, same treatment
20. nginx.conf: Authorization pass-through

── Phase F: Admin UI ────────────────────────────────────────────────────────
21. Admin UI: Settings page — API key section (add/remove/status)
22. Admin UI: Settings page — Usage Dashboard tab (per-model table + aggregate)
23. Admin UI: Playground — no-key banner + per-model usage chip + 429 inline error
24. Admin UI: UserDto updated (keyConfigured, todayTotalRequests)
25. Admin UI: UserManager — key badge + expandable usage row
26. Admin UI: Usage Limits admin page (global defaults + per-user overrides)

── Phase G: Validation ──────────────────────────────────────────────────────
27. CLAUDE.md update
28. docker compose down -v && docker compose up (apply V4 migration cleanly)
29. Verify: register → save key → chat → confirm per-user key in Authorization header
30. Verify: exhaust daily request limit → confirm 429 with resetAt in response
31. Verify: admin sets limit override → confirm user sees new limit in usage dashboard
```

---

## Key Constraints (carry forward from CLAUDE.md)

- Flyway boolean columns must be `BIT(1)` — never `BOOLEAN` or `TINYINT`
- Never edit applied migrations (V1–V3 locked; V4 is next)
- Never expose the stored key value via any API endpoint — status only
- nginx `OPENROUTER_API_KEY` remains in `.env` — do not remove it
- Gradle wrapper only; Java 21 runtime for Gradle, Java 25 for compilation
- All new service methods: SLF4J logging at appropriate levels
- All usage dates/times are UTC — `LocalDate.now(ZoneOffset.UTC)` and `LocalDateTime.now(ZoneOffset.UTC)`
- Token hard block is post-call (tokens unknown before response); request count hard block is pre-call
- `model_usage_limits` global default row has `user_id = NULL` — queries must use `IS NULL`, not `= NULL`
- No scheduled reset job — usage windows are date-keyed; a new day automatically starts a fresh row

---

## Open Questions (resolve before implementation)

1. Should users be blocked from registering without agreeing to provide a key
   (i.e., gate registration on key requirement explanation), or is the Settings
   page prompt post-login sufficient?
2. Should the Playground be fully hidden (route guard) until a key is set,
   or show the banner inline? Banner is friendlier UX.
3. Key rotation: if a user updates their key, should we re-validate the new one
   against OpenRouter before saving? Yes — always validate on save.
4. Should the usage chip in the Playground update after every message (adds a GET call
   per response) or only on page load? Per-message is more accurate but chattier.
5. Should admin be able to reset a specific user's usage counter for a given day
   (e.g., if a user hit a wrong limit)? Nice-to-have, can be a V5 addition.
6. Default system limits (50 req / 100k tokens per day) — are these the right defaults
   for launch, or should they be configurable via application.properties?

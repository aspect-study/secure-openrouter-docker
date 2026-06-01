# CLAUDE.md — Secure OpenRouter Gateway

This file is the entry point for any AI session or new developer working on this project.
Read this before touching any code.

---

## Project Overview

A secure, local API gateway for OpenRouter's free LLM models.
Three-layer architecture: nginx reverse proxy → Spring Boot backend → MySQL.
Phase 4 adds a React Admin UI + AI Playground.

```
Client (browser :3000)
  │
  ▼ HTTP :8080
Spring Boot (JWT auth, rate limiting, logging, conversations)
  │
  ▼ HTTP :8081 (localhost only)
nginx reverse proxy (token injection, TLS to OpenRouter)
  │
  ▼ HTTPS
openrouter.ai (free models only)
  │
  ▼
MySQL :3309 (users, chat_logs, conversations, conversation_messages, model_config)
```

---

## Repository Structure

```
secure-openrouter-docker/
├── app/                          # Spring Boot Java 25 application
│   ├── src/main/java/com/openrouter/gateway/
│   │   ├── admin/                # AdminController — ROLE_ADMIN endpoints
│   │   ├── auth/                 # JWT, User entity, register/login
│   │   ├── chat/                 # ChatController, ChatService, OpenRouterClient
│   │   ├── config/               # SecurityConfig, HttpClientConfig, AppProperties, ModelConfig
│   │   ├── conversation/         # Conversation, ConversationMessage, ConversationController,
│   │   │                         # ConversationService, MarkdownNormalizer
│   │   ├── exception/            # GlobalExceptionHandler
│   │   ├── logging/              # ChatLog entity + repository
│   │   └── ratelimit/            # Bucket4j per-user rate limiting
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/         # Flyway migrations (schema owner — see ADR-010)
│   │       ├── V1__initial_schema.sql      # All 5 tables
│   │       ├── V2__seed_model_config.sql   # 24 free model rows
│   │       └── V3__seed_admin_user.sql     # Default admin user
│   ├── build.gradle              # Groovy DSL (NOT Kotlin DSL — see ADR-003)
│   ├── settings.gradle
│   ├── Dockerfile                # Multi-stage: Java 21 build → Java 25 JRE runtime
│   └── gradlew.bat               # Always use the wrapper, never global gradle
├── admin-ui/                     # React + Vite + shadcn/ui admin dashboard (branded: AspectOR)
│   ├── src/
│   │   ├── hooks/                # useAuth.ts, AuthProvider.tsx, useWindowSize.ts
│   │   ├── lib/                  # api.ts (Axios), utils.ts, theme.ts
│   │   ├── components/
│   │   │   ├── layout/           # AdminLayout.tsx (responsive sidebar)
│   │   │   └── ui/               # shadcn/ui + chat-message.tsx, change-password-dialog.tsx
│   │   └── pages/
│   │       ├── LoginPage.tsx     # Sign In + Sign Up tabs
│   │       ├── PlaygroundPage.tsx
│   │       └── admin/            # Dashboard, ChatLogs, ModelManager, UserManager
│   ├── Dockerfile                # Multi-stage: Node build → nginx serve
│   ├── nginx.conf                # SPA routing (all → index.html)
│   ├── tailwind.config.ts        # darkMode: class, colors use var() not hsl(var())
│   └── components.json           # shadcn style: radix-nova
├── db/
│   └── seed.sql                  # DEPRECATED — reference only; schema now owned by Flyway
├── Dockerfile                    # nginx proxy image
├── docker-compose.yml            # All 4 services
├── nginx.conf                    # Hardened nginx config
├── docker-entrypoint.sh          # envsubst token injection
├── .env.example                  # Template — copy to .env, never commit .env
├── run-app.bat                   # Loads .env + starts Spring Boot locally
├── test-request.ps1              # PowerShell smoke test for nginx proxy
├── test-request.sh               # Bash smoke test for nginx proxy
├── test-models.ps1               # Tests all free models against the proxy
└── memory/
    ├── adrs/                     # Architectural Decision Records
    ├── prds/                     # Product Requirements Documents
    └── learnings/                # Hard lessons from development
```

---

## Build Commands

### Prerequisites
- Docker Desktop running
- Java 21 in PATH for running Gradle (see ADR-004)
- Java 25 installed at `C:\Program Files\Java\jdk-25`
- Node 22 + npm 10 for admin-ui local dev

### Start all infrastructure (nginx + MySQL)
```cmd
docker compose up -d openrouter-proxy openrouter-mysql
docker compose ps   # verify both show (healthy)
```

### Run Spring Boot locally
```cmd
cd C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker
run-app.bat
```
This loads `.env`, switches to Java 21, and runs `gradlew.bat bootRun`.

### Run admin UI locally (dev mode)
```cmd
cd admin-ui
npm run dev
```
Vite proxies `/api` → `localhost:8080`. Open `http://localhost:3000`.

### Build Spring Boot (no tests)
```cmd
cd app
gradlew.bat build -x test
```

### Build admin-ui for production
```cmd
cd admin-ui
npm run build
```

### Run full stack in Docker
```cmd
docker compose up -d
docker compose ps   # wait for all 4 (healthy)
```
Access admin UI at `http://localhost:3000`.

---

## Environment Variables

All secrets live in `.env` (never committed). Copy from `.env.example`.

| Variable | Used by | Notes |
|---|---|---|
| `OPENROUTER_API_KEY` | nginx | Injected at container startup via envsubst |
| `DEFAULT_MODEL` | test scripts | Default free model for smoke tests |
| `MYSQL_ROOT_PASSWORD` | MySQL container | Rarely used directly |
| `MYSQL_DATABASE` | MySQL + Spring Boot | `openrouter_gateway` |
| `MYSQL_USER` | MySQL + Spring Boot | App-level DB user |
| `MYSQL_PASSWORD` | MySQL + Spring Boot | App-level DB password |
| `JWT_SECRET` | Spring Boot | Base64-encoded, minimum 256 bits (32 bytes) |
| `JWT_EXPIRATION_MS` | Spring Boot | Default: 86400000 (24 hours) |
| `OPENROUTER_PROXY_URL` | Spring Boot | `http://localhost:8081` (local) or `http://openrouter-proxy:8080` (Docker) |
| `ENCRYPTION_MASTER_KEY` | Spring Boot | 64-char hex (32 bytes) for AES-GCM key encryption. Generate: `openssl rand -hex 32` |

---

## API Endpoints

### Auth (public)
```
POST /api/auth/register          {"email": "...", "password": "..."}
POST /api/auth/login             {"email": "...", "password": "..."}
POST /api/auth/change-password   {"currentPassword": "...", "newPassword": "..."}  (requires JWT)
```

### Chat (requires JWT — ROLE_USER or ROLE_ADMIN)
```
POST /api/chat/completions                        {"model": "...", "messages": [...]}
GET  /api/chat/models                             Returns allowed free model list
GET  /api/conversations                           List user's conversations
POST /api/conversations                           Create conversation {"model": "...", "title": "..."}
GET  /api/conversations/{id}                      Get conversation with messages
POST /api/conversations/{id}/messages             Send message (blocking, full response)
POST /api/conversations/{id}/messages/stream      Send message (SSE streaming)
DELETE /api/conversations/{id}                    Delete conversation
```

**SSE stream event protocol:**
```
event: token   data: "<JSON-encoded token string>"   (one per delta)
event: done    data: {"messageId":1,"conversationId":1,"title":"...","normalizedContent":"...","usage":{...}}
event: error   data: {"error":"...","remainingTokens":0}
```

### API Key — BYOK (requires JWT)
```
PUT    /api/user/api-key             {"apiKey": "sk-or-v1-..."} — validate + save
DELETE /api/user/api-key                                         — remove key
GET    /api/user/api-key/status                                  — {configured: true|false}
```

### Usage (requires JWT)
```
GET  /api/user/usage              — today's per-model usage + global aggregate
GET  /api/user/usage/{modelId}    — today's usage for a specific model
```

### User Model Preferences — PRD-003 (requires JWT — ROLE_USER or ROLE_ADMIN)
```
GET    /api/user/models              — full model list with adminEnabled/userEnabled/effectivelyEnabled per entry
PUT    /api/user/models/{id}/toggle  — atomically flip user preference; {id} = model_config integer PK
GET    /api/user/models/{id}/status  — single model state; {id} = model_config integer PK
```

**Why integer PK, not string modelId in path:** Model IDs (e.g., `meta-llama/llama-3.3-70b-instruct:free`)
contain forward slashes. Tomcat normalises `%2F` before Spring MVC sees the request. Using the
`model_config.id` integer avoids this entirely. The modelId string is in response bodies only.

**Response shape — `GET /api/user/models`:**
```json
{
  "models": [
    { "id": 3, "modelId": "meta-llama/...", "name": "Llama 3.3 70B", "adminEnabled": true, "userEnabled": true, "effectivelyEnabled": true }
  ],
  "totalAdminEnabled": 12,
  "totalUserEnabled": 8
}
```
`totalUserEnabled` = effective visible count (admin-enabled ∩ user-enabled); NOT the count of DB rows.

**ROLE_ADMIN bypass:** Admin callers always receive all globally-enabled models regardless of saved preference rows.

### Admin (requires JWT — ROLE_ADMIN only)
```
GET  /api/admin/stats
GET  /api/admin/chat-logs?page=0&size=20&user=&model=&from=&to=
GET  /api/admin/chat-logs/export     CSV download
GET  /api/admin/models
PUT  /api/admin/models/{modelId}/toggle
GET  /api/admin/users
PUT  /api/admin/users/{id}/role      {"role": "USER"|"ADMIN"}
PUT  /api/admin/users/{id}/status    {"active": true|false}
GET  /api/admin/usage/limits                              — all global defaults
PUT  /api/admin/usage/limits/{modelId}                    — set/update global default {"maxRequestsPerDay":50,"maxTokensPerDay":100000}
GET  /api/admin/users/{id}/usage/limits                   — user's overrides
PUT  /api/admin/users/{id}/usage/limits/{modelId}         — set/update user override
DELETE /api/admin/users/{id}/usage/limits/{modelId}       — remove override (falls back to global)
GET  /api/admin/users/{id}/usage                          — today's per-model usage for a user
```

### System
```
GET /actuator/health                 Spring Boot health (public)
GET http://localhost:8081/health     nginx proxy health
```

---

## Database Schema

Tables managed by Hibernate `ddl-auto=update` except `model_config` which is seeded via `db/seed.sql`:

- `users` — email, password_hash (BCrypt), role (USER/ADMIN), active, timestamps; + V4: openrouter_key_encrypted (AES-GCM), openrouter_key_validated (BIT), openrouter_key_set_at
- `chat_logs` — per-request log: user, model, tokens, latency, status, response preview
- `model_config` — enabled/disabled state per free model, seeded from `db/seed.sql`
- `conversations` — per-user chat sessions: title, model, timestamps
- `conversation_messages` — messages per conversation: role (user/assistant), content
- `model_usage_limits` — V4: admin-controlled daily limits per model; user_id=NULL = global default
- `user_model_usage` — V4: daily per-user-per-model counters (request_count, token_count, reset_at)
- `user_model_preferences` — V5: per-user model toggle state (sparse — absence of row = enabled by default)

---

## Default Admin Credentials

Created by `db/seed.sql` on first Docker MySQL startup.
Register via API and update role via Navicat if seed doesn't run automatically.

```
Email:    admin@openrouter.local
Password: Admin@2026!
```
**Change this password after first login.**

---

## Allowed Free Models

Verified 2026-05-29 via `test-models.ps1`. Update when models change.
Check: https://openrouter.ai/models?max_price=0

```
nvidia/nemotron-nano-9b-v2:free          ← default, most reliable
meta-llama/llama-3.3-70b-instruct:free
meta-llama/llama-3.2-3b-instruct:free
deepseek/deepseek-v4-flash:free
qwen/qwen3-coder:free
google/gemma-4-31b-it:free
openai/gpt-oss-120b:free
openai/gpt-oss-20b:free
... (see OpenRouterClient.java FREE_MODELS for full list)
```

Update in two places when models change:
1. `OpenRouterClient.java` — `FREE_MODELS` set
2. `ModelConfig` seed in `db/seed.sql`

---

## Key Constraints

- **Schema is owned by Flyway** — `ddl-auto=validate`; never change back to `update` or `create`
- **Never edit applied Flyway migrations** — V1/V2/V3 are locked; add V4+ for any schema change
- **Flyway boolean columns must be `BIT(1)`** — Hibernate 6 / MySQL8Dialect maps Java `boolean` → `Types#BOOLEAN` → `BIT`; using `TINYINT` causes `SchemaManagementException` on startup
- **Never hardcode the OpenRouter API key** — envsubst only, never in image layers
- **ENCRYPTION_MASTER_KEY must be 64 hex chars (32 bytes)** — AES-GCM key for encrypting stored user OpenRouter keys. Generate: `openssl rand -hex 32`. Set in `.env` and `docker-compose.yml`.
- **Never expose stored OpenRouter API keys via HTTP** — `openrouterKeyEncrypted` is decrypted in-process only; no endpoint returns the plaintext value
- **nginx Authorization is now pass-through** — Spring Boot sets `Authorization: Bearer {userKey}`; nginx forwards it with `$http_authorization`. The `OPENROUTER_API_KEY` env var is no longer injected for chat (kept for health/backwards compat)
- **`model_usage_limits` global row has `user_id = NULL`** — always use `IS NULL` in queries, never `= NULL`
- **No usage reset scheduled job** — usage windows are date-keyed; a new UTC day creates a fresh row automatically
- **Request limit is pre-call, token limit is post-call** — request count is checked before forwarding (unknown tokens). Token count is checked post-call; if over limit, next call is hard-blocked
- **KeyNotConfiguredException → 409** — user tried to chat without a saved API key; frontend must redirect to Settings
- **UsageLimitExceededException → 429 with `resetAt`** — daily limit reached; response includes `limitType` ("request"/"tokens") and ISO `resetAt`
- **Never expose ports on 0.0.0.0** — all ports bind to `127.0.0.1` only
- **Gradle wrapper only** — never run `gradle` directly; always use `gradlew.bat`
- **Gradle runtime must be Java 21** — Gradle 8.14 does not support Java 25 runtime (ADR-004). Gradle Toolchain auto-provisions JDK 25 for compilation. Gradle 8.14 itself still requires JDK 21 in PATH.
- **JWT secret must be Base64-encoded and ≥ 256 bits** — JJWT enforces at startup
- **MySQL port is 3309** — 3306/3307 taken by other local instances
- **nginx proxy port is 8081** — 8080 used by Spring Boot locally
- **Tailwind colors must use `var()` not `hsl(var())`** — shadcn radix-nova uses oklch CSS variables (ADR-006)
- **Use CommandDialog not custom overlay** — cmdk keyboard nav requires proper focus trap (ADR-007)
- **Wrap CommandInput/CommandList in `<Command>`** — this `CommandDialog` does NOT auto-wrap children (ADR-007)
- **Model switching starts a new conversation** — conversation.model is immutable after creation; switching model creates fresh conversation
- **429 from OpenRouter is NOT a ChatResult.RateLimited** — it comes through as `Success(statusCode=429)`; check statusCode before saving
- **react-markdown requires custom `code` component** — default rendering shows raw fences; override with react-syntax-highlighter
- **Tabs radix-nova renders side-by-side** — add `className="flex-col"` to force vertical stacking
- **SSE token data must be JSON-encoded** — raw `\n` in SSE `data:` fields is treated as an empty line by the protocol, silently dropping newlines. Use `objectMapper.writeValueAsString(token)` on the backend; `JSON.parse(data)` on the frontend
- **SseEmitter.complete() triggers ASYNC dispatcher** — Spring Security intercepts the async re-dispatch with no SecurityContext and throws `Access Denied`. Fix: `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` in SecurityConfig
- **useAuth must be a shared React Context** — calling `useAuth()` in multiple components creates independent useState instances; login updates one copy while ProtectedRoute reads another fresh null, bouncing back to /login. AuthProvider in main.tsx is the fix
- **Axios 401 interceptor must guard auth endpoints** — wrong password on `/api/auth/login` returns 401; the interceptor must check `!isAuthEndpoint && hadToken` before redirecting, otherwise login always fails
- **remark-gfm plugin required for GFM tables** — react-markdown does not parse pipe tables by default; must add `remarkPlugins={[remarkGfm]}`
- **Frontend normalizeMarkdown must skip during streaming** — running normalization on partial content (mid-stream) mis-detects separator rows; pass `isStreaming` prop and skip normalization while true
- **Write tool pads files with null bytes** — files written via the Write tool in this environment contain null byte padding after content. Always strip with `tr -d '\0'` or `truncate -s -1` after any Write/Edit operation that causes TypeScript "Invalid character" errors
- **PRD-003: Admin gates, user filters** — users can never re-enable an admin-disabled model; `toggleModel` rejects attempts with 400 (`ModelAdminDisabledException`)
- **PRD-003: ROLE_ADMIN bypasses user preferences** — `getEffectiveModels` short-circuits to full admin-enabled list when caller has ROLE_ADMIN; preference rows for admins are ignored
- **PRD-003: Absence of preference row = enabled** — `user_model_preferences` is sparse; do not pre-populate rows for every user/model combination
- **PRD-003: `updated_at` is DB-managed** — `user_model_preferences.updated_at` uses `ON UPDATE CURRENT_TIMESTAMP`; the entity field is `insertable=false, updatable=false` — never set in application code
- **PRD-003: `userId` from JWT only** — all preference endpoints resolve userId via `@AuthenticationPrincipal String email` → user lookup; no endpoint accepts userId as a path or query parameter
- **PRD-003: `totalUserEnabled` = effective visible count** — not the count of explicit `enabled=true` DB rows; derived from admin-enabled ∩ user-enabled at query time
- **PRD-003: Integer PK in path, never string modelId** — model IDs contain forward slashes incompatible with Spring MVC path variables; all toggle/status paths use `model_config.id` (integer)
- **PRD-003: `toggleModel` uses atomic upsert** — `INSERT ... ON DUPLICATE KEY UPDATE enabled = NOT enabled`; load-or-create is forbidden (race condition: concurrent INSERTs violate unique constraint or last-write-wins produces wrong state)
- **PRD-003: `/api/chat/models` unchanged** — Playground switches to `/api/user/models`; the admin-facing chat models endpoint is unmodified
- **PRD-003: Empty-state condition lives in `useEffectiveModels` hook** — `totalUserEnabled === 0` check must not be duplicated across My Models page and Playground; both derive from the same hook
- **PRD-003: Optimistic UI must revert on failure** — toggle reverts to pre-click state on API error, accompanied by an error toast
- **PRD-003: No FK on `user_model_preferences.model_id`** — orphaned rows (from removed model_config entries) are harmless; `getEffectiveModels` excludes them via `model_config` JOIN
- **PRD-003: SecurityConfig rule** — `/api/user/models/**` → `hasAnyRole("USER", "ADMIN")` added before the `anyRequest()` catch-all
- **PRD-003: Flyway V5** — `user_model_preferences` table; V1–V5 are locked; next schema change is V6
- **@Transactional required on any controller method accessing lazy collections** — Spring closes the Hibernate session after each repository call. Any method that calls `entity.getLazyCollection()` after loading via a repository must be `@Transactional` (readOnly for GETs, default for writes). Missing this causes `LazyInitializationException`.
- **AuthorizationDeniedException must have a dedicated handler** — `AuthProvider` probes `/api/admin/stats` after every login to detect admin status; regular users always get 403. Without a specific handler it falls through to the catch-all and logs at ERROR. Handler returns 403 at DEBUG level — no stack trace.
- **Upstream 429 in SSE must send error event before completeWithError()** — generic catch calling `completeWithError()` directly gives the client no feedback. Always send `event: error` first. Detect upstream 429 by `e.getMessage().contains("stream error 429")` and log at WARN, not ERROR.
- **My Models and Model Manager share the same OWNER_GROUPS categorisation** — both group by owner (NVIDIA, Meta, Google, etc.) with emoji headers and All/Enabled/Disabled filter tabs. If a new provider is added, update `OWNER_GROUPS` in both `ModelManagerPage.tsx` and `MyModelsTab.tsx`.

---

## Troubleshooting Quick Reference

| Symptom | Likely cause | Fix |
|---|---|---|
| `SchemaManagementException: wrong column type [enabled/active]` | Flyway V1 used `TINYINT` instead of `BIT(1)` | Hibernate 6 expects `BIT` for Java `boolean`; drop DB and re-run with corrected V1 |
| Flyway checksum mismatch on startup | Applied migration file was edited | Never edit V1–V3; create V4+ for changes; dev reset: drop DB and re-run |
| `WeakKeyException` on startup | JWT_SECRET too short | Regenerate: `powershell -command "[Convert]::ToBase64String((1..32 \| ForEach-Object { [byte](Get-Random -Max 256) }))"` |
| nginx container restarting | `OPENROUTER_API_KEY` missing | Add key to `.env` |
| Spring Boot fails with column error | New entity field, Hibernate not updated | Restart Spring Boot; ddl-auto=update adds columns |
| Admin login fails | User not in DB or wrong hash | Register via API, update role via Navicat |
| Dark mode not working | CSS variable format mismatch | Ensure tailwind.config.ts uses `var()` not `hsl(var())` |
| ↑↓ not working in command palette | Missing `Command` wrapper in `CommandDialog` | Wrap CommandInput/CommandList in `<Command>` inside CommandDialog |
| Model switch not working | Conversation stores model at creation | Switching model creates a new conversation — this is by design |
| Empty bubble after 429 | Upstream error treated as success | `ConversationController` checks `s.statusCode() >= 400` and rolls back |
| Code blocks render as plain text | Missing react-markdown `components` prop | Pass custom `code` renderer with SyntaxHighlighter to `<ReactMarkdown>` |
| Tabs side by side instead of stacked | radix-nova `data-horizontal:flex-col` doesn't match | Add `className="flex-col"` to `<Tabs>` |
| Change password 401 | JWT not sent or user not found | Ensure Bearer token is in Authorization header; user must be active |
| Login spins forever / redirects back | 401 interceptor firing on wrong-password response | Check `api.ts` interceptor guards `!isAuthEndpoint && hadToken` before redirect |
| Login always redirects to /login after success | useAuth not in shared context | Ensure `<AuthProvider>` wraps the app in `main.tsx`; all components must share one auth instance |
| SSE stream works but tables collapse to one line | `\n` tokens dropped by SSE protocol | Backend: `objectMapper.writeValueAsString(token)`; Frontend: `JSON.parse(data)` before append |
| `Access Denied` after SSE stream completes | Tomcat ASYNC dispatcher has no SecurityContext | Add `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` to SecurityConfig |
| TypeScript "Invalid character" on line N | Null byte padding from Write tool | Run `tr -d '\0' < file > file.tmp && cp file.tmp file` or `truncate -s -1 file` |
| Markdown tables render as raw text | Missing remark-gfm plugin | Add `remarkPlugins={[remarkGfm]}` to ReactMarkdown; install with `npm install remark-gfm` |
| `No endpoints found` from OpenRouter | Model removed from free tier | Run `test-models.ps1`, update `FREE_MODELS` |
| `429 rate limited` from OpenRouter | Free tier upstream throttle | Wait 30s, try different model |
| Gradle requires JDK 21 in PATH | `./gradlew` fails with wrong Java version | Ensure `JAVA_HOME` or PATH points to JDK 21 — Toolchain handles JDK 25 compilation automatically |
| `outline-ring/50` CSS error | shadcn radix-nova opacity modifier incompatible | Remove `outline-ring/50` from index.css |
| Chat returns 409 | User has no API key configured | User must go to Settings → add OpenRouter API key |
| `IllegalArgumentException: ENCRYPTION_MASTER_KEY must be 64 hex chars` | Key missing or wrong format | Generate: `openssl rand -hex 32`, set in `.env` |
| Flyway V4 checksum mismatch | V4 was edited after apply | Never edit V4; create V5+ for changes |
| `KeyNotConfiguredException` in logs | getKeyForUser called for user with no key | Expected — 409 returned to client; no action needed |
| Usage not incrementing | Token count 0 from OpenRouter | OpenRouter sometimes omits `usage` in stream; counters stay at 0 for that request |
| `PUT /api/user/models/{id}/toggle` returns 400 | Model is admin-disabled | Admin must enable the model first; user cannot override admin gate |
| `PUT /api/user/models/{id}/toggle` returns 404 | Wrong id — must be model_config integer PK, not string modelId | Use `UserModelDto.id` (number) from `GET /api/user/models` response |
| Playground dropdown empty after toggle | `useEffectiveModels` not refreshed | Toggle refreshes hook state automatically; if stale, call `refresh()` from hook |
| My Models tab shows no models | `GET /api/user/models` failed or returned empty | Check Spring Boot logs; verify V5 migration applied (`SELECT * FROM flyway_schema_history`) |
| Flyway V5 checksum mismatch | V5 was edited after apply | Never edit V5; create V6 for schema changes |
| `SchemaManagementException` on `user_model_preferences.enabled` | Column created as BOOLEAN/TINYINT instead of BIT(1) | Drop DB and re-run with corrected V5 using `BIT(1)` |
| Toggle optimistic UI doesn't revert | Error toast fires but state sticks | Ensure `setOptimisticOverrides` revert path runs in catch block of `handleToggle` |
| Admin sees preference rows affecting their model list | Admin bypass not firing | Verify `isAdmin` flag is derived from `user.getRole() == Role.ADMIN` in controller, passed to service |
| `LazyInitializationException` on `Conversation.messages` | Controller method loads entity without `@Transactional`; session closes before lazy collection is accessed | Add `@Transactional(readOnly = true)` to `get()` and `@Transactional` to `sendMessage()` in ConversationController |
| `ERROR Unhandled exception: Access Denied` on every user login | `AuthorizationDeniedException` from `/api/admin/stats` probe falls through to catch-all | Add dedicated `@ExceptionHandler(AuthorizationDeniedException.class)` returning 403 at DEBUG level |
| SSE stream closes silently on upstream 429 — no error shown in UI | Generic catch calls `completeWithError()` without sending an `event: error` SSE event first | Detect `"stream error 429"` in message, send error event to client, log at WARN not ERROR |
| My Models tab shows flat list, Model Manager shows grouped | `OWNER_GROUPS` was only in `ModelManagerPage` | Both views now share the same grouping logic; update `OWNER_GROUPS` in both files when providers change |

---

## Roadmap

- [x] Phase 1 — Secure nginx proxy prototype
- [x] Phase 2 — Spring Boot JWT gateway, rate limiting, chat logging
- [x] Phase 3 — Dockerize Spring Boot (multi-stage Dockerfile)
- [x] Phase 4 — Admin UI + AI Playground (React + shadcn/ui)
- [x] Phase 4.5 — SSE streaming, markdown quality, auth context fix, login bug fixes
- [x] Phase 4.6 — PRD-002: BYOK (per-user OpenRouter API key), AES-GCM encryption, daily usage tracking, usage limits admin
- [x] Phase 4.7 — PRD-003: User-level model preferences (My Models tab, Playground scoping, sparse preference table)
- [ ] Phase 5 — GitHub Actions CI/CD pipeline

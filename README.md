# AspectOR — Secure OpenRouter Gateway

A self-hosted, secure API gateway for [OpenRouter](https://openrouter.ai) free-tier LLMs.
JWT-authenticated, rate-limited, fully logged, with a per-user BYOK system, usage limits,
model preferences, an admin dashboard, and an AI Playground with SSE streaming.

> **Stack:** Docker · nginx · Spring Boot 3.5 (Java 25) · MySQL 8 · React + Vite + shadcn/ui  
> **Tested on:** Windows 11 (Docker Desktop + PowerShell)

---

## Architecture

```
Browser (localhost:3000)
  │
  ▼ HTTP :8080
Spring Boot — JWT auth, rate limiting (Bucket4j), chat logging,
              conversations, BYOK key management, usage tracking,
              user model preferences
  │
  ▼ HTTP :8081 (localhost only)
nginx reverse proxy — Authorization pass-through, TLS to OpenRouter
  │
  ▼ HTTPS
openrouter.ai (free models only)
  │
  ▼
MySQL :3309 — users, chat_logs, conversations, conversation_messages,
              model_config, model_usage_limits, user_model_usage,
              user_model_preferences
```

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | 4.x+ | Includes Compose v2 |
| Java | 21 (Gradle runtime) + 25 (app toolchain) | See ADR-004 |
| Node | 22 + npm 10 | Admin UI local dev only |

---

## Quick Start

```cmd
cp .env.example .env        :: fill in all required secrets (see below)

docker compose up -d        :: starts all 4 services
docker compose ps           :: wait for all (healthy)
```

Open **http://localhost:3000** — log in with `admin@openrouter.local` / `Admin@2026!`.  
**Change the admin password immediately after first login.**

---

## Environment Variables

All secrets live in `.env` — never committed. Copy from `.env.example`.

| Variable | Used by | Notes |
|---|---|---|
| `OPENROUTER_API_KEY` | nginx | Kept for health/compat; chat now uses per-user BYOK key |
| `DEFAULT_MODEL` | test scripts | Default free model for smoke tests |
| `MYSQL_ROOT_PASSWORD` | MySQL | |
| `MYSQL_DATABASE` | MySQL + Spring Boot | `openrouter_gateway` |
| `MYSQL_USER` | MySQL + Spring Boot | |
| `MYSQL_PASSWORD` | MySQL + Spring Boot | |
| `JWT_SECRET` | Spring Boot | Base64-encoded, minimum 256 bits (32 bytes) |
| `JWT_EXPIRATION_MS` | Spring Boot | Default: `86400000` (24h) |
| `OPENROUTER_PROXY_URL` | Spring Boot | `http://localhost:8081` local · `http://openrouter-proxy:8080` Docker |
| `ENCRYPTION_MASTER_KEY` | Spring Boot | 64-char hex (32 bytes) for AES-GCM encryption of stored user API keys |

Generate a JWT secret:
```powershell
powershell -command "[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))"
```

Generate an encryption master key:
```powershell
openssl rand -hex 32
```

---

## Schema Management

Schema is owned by **Flyway** (`ddl-auto=validate`). Migrations in `app/src/main/resources/db/migration/`:

| Migration | Content |
|---|---|
| `V1__initial_schema.sql` | Core tables: users, chat_logs, conversations, conversation_messages, model_config |
| `V2__seed_model_config.sql` | 24 free model rows |
| `V3__seed_admin_user.sql` | Default admin user |
| `V4__usage_tracking.sql` | model_usage_limits, user_model_usage — daily usage tracking + limits |
| `V5__user_model_preferences.sql` | user_model_preferences — per-user model toggle state (sparse) |

**Never edit V1–V5 after they have been applied.** Add `V6+` for any schema change.

Flyway runs automatically on startup. To reset (development only):
```sql
DROP DATABASE openrouter_gateway;
CREATE DATABASE openrouter_gateway CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
Then restart — Flyway re-applies all migrations from scratch.

---

## Running Locally (Development)

```cmd
:: Start infrastructure
docker compose up -d openrouter-proxy openrouter-mysql
docker compose ps           :: wait for both (healthy)

:: Spring Boot (loads .env, switches to Java 21, runs gradlew bootRun)
run-app.bat

:: Admin UI (Vite dev server — proxies /api → :8080)
cd admin-ui && npm run dev
```

Build Spring Boot without tests:
```cmd
cd app && gradlew.bat build -x test
```

---

## API Endpoints

### Auth (public)
```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/change-password          (JWT required)
```

### Chat (JWT — ROLE_USER or ROLE_ADMIN)
```
POST /api/chat/completions
GET  /api/chat/models
```

### Conversations (JWT — ROLE_USER or ROLE_ADMIN)
```
GET    /api/conversations
POST   /api/conversations
GET    /api/conversations/{id}
POST   /api/conversations/{id}/messages         blocking
POST   /api/conversations/{id}/messages/stream  SSE streaming
DELETE /api/conversations/{id}
```

SSE stream events: `token` (delta string, JSON-encoded) · `done` (messageId, title, usage) · `error`

### BYOK API Key (JWT)
```
PUT    /api/user/api-key        {"apiKey": "sk-or-v1-..."}
DELETE /api/user/api-key
GET    /api/user/api-key/status
```

### Usage (JWT)
```
GET /api/user/usage
GET /api/user/usage/{modelId}
```

### User Model Preferences (JWT — ROLE_USER or ROLE_ADMIN)
```
GET /api/user/models
PUT /api/user/models/{id}/toggle    {id} = model_config integer PK (not string modelId)
GET /api/user/models/{id}/status
```

### Admin (JWT — ROLE_ADMIN)
```
GET  /api/admin/stats
GET  /api/admin/chat-logs               ?page=0&size=20&user=&model=&from=&to=
GET  /api/admin/chat-logs/export        CSV
GET  /api/admin/models
PUT  /api/admin/models/{modelId}/toggle
GET  /api/admin/users
PUT  /api/admin/users/{id}/role
PUT  /api/admin/users/{id}/status
GET  /api/admin/usage/limits
PUT  /api/admin/usage/limits/{modelId}
GET  /api/admin/users/{id}/usage/limits
PUT  /api/admin/users/{id}/usage/limits/{modelId}
DELETE /api/admin/users/{id}/usage/limits/{modelId}
GET  /api/admin/users/{id}/usage
```

### System
```
GET /actuator/health
GET http://localhost:8081/health        nginx proxy health
```

---

## Free Models (verified 2026-05-29)

Check current availability: https://openrouter.ai/models?max_price=0

```
nvidia/nemotron-nano-9b-v2:free         ← default, most reliable
meta-llama/llama-3.3-70b-instruct:free
meta-llama/llama-3.2-3b-instruct:free
deepseek/deepseek-v4-flash:free
qwen/qwen3-coder:free
google/gemma-4-31b-it:free
openai/gpt-oss-120b:free
openai/gpt-oss-20b:free
```

Full list in `OpenRouterClient.java` (`FREE_MODELS`) and `V2__seed_model_config.sql`.

---

## Security

- API key injected at runtime via `envsubst` — never baked into image layers
- User OpenRouter keys encrypted at rest with AES-GCM (`ENCRYPTION_MASTER_KEY`)
- All ports bound to `127.0.0.1` only
- Container runs as non-root (`nginx` user, uid 101)
- Root filesystem is read-only (`read_only: true`)
- All Linux capabilities dropped (`cap_drop: ALL`)
- JWT secret enforced minimum 256 bits at startup (JJWT)
- BCrypt strength 12

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `WeakKeyException` on startup | JWT_SECRET too short | Regenerate — min 32 bytes Base64 |
| nginx restarting | `OPENROUTER_API_KEY` missing | Add key to `.env` |
| `SchemaManagementException: wrong column type [enabled]` | Boolean column created as TINYINT instead of BIT(1) | Hibernate 6 maps Java `boolean` → `BIT`; use `BIT(1)` in all Flyway migrations |
| Flyway checksum mismatch | Applied migration edited after apply | Never edit V1–V5; add V6+ instead; drop DB for dev reset |
| `IllegalArgumentException: ENCRYPTION_MASTER_KEY must be 64 hex chars` | Key missing or wrong format | Generate: `openssl rand -hex 32`, set in `.env` |
| Admin login fails | User not seeded | Flyway V3 seeds admin on fresh DB; check `users` table |
| Chat returns 409 | User has no API key configured | Go to Settings → add OpenRouter API key |
| `LazyInitializationException: could not initialize proxy - no Session` | Controller method accesses lazy collection without `@Transactional` | Add `@Transactional(readOnly=true)` to GET methods, `@Transactional` to write methods in ConversationController |
| `ERROR Unhandled exception: Access Denied` on every user login | `AuthorizationDeniedException` from admin stats probe falls through to catch-all | Fixed: dedicated handler in GlobalExceptionHandler returns 403 at DEBUG level |
| SSE stream closes silently on upstream 429 — no error shown in UI | Generic catch called `completeWithError()` without sending an `event: error` first | Fixed: upstream 429 detected, error event sent to client, logged at WARN |
| Dark mode not working | Tailwind color format wrong | Use `var()` not `hsl(var())` — radix-nova uses oklch |
| Model switch not working | Conversation model is immutable | Switching model creates a new conversation — by design |
| Empty bubble after upstream 429 | 429 treated as success | `ConversationController.sendMessage` checks `statusCode >= 400`, rolls back |
| Gradle build fails with `version 69` | Gradle running on Java 25 | Ensure Java 21 in PATH; Gradle toolchain handles Java 25 compilation |
| `PUT /api/user/models/{id}/toggle` returns 404 | Wrong id — must be model_config integer PK, not string modelId | Use `UserModelDto.id` (number) from `GET /api/user/models` response |

---

## Repository Structure

```
secure-openrouter-docker/
├── app/                                    # Spring Boot Java 25
│   ├── src/main/java/com/openrouter/gateway/
│   │   ├── admin/                          # Admin endpoints
│   │   ├── auth/                           # JWT, User entity, register/login
│   │   ├── chat/                           # ChatController, ChatService, OpenRouterClient
│   │   ├── config/                         # SecurityConfig, HttpClientConfig, AppProperties
│   │   ├── conversation/                   # Conversations + SSE streaming
│   │   ├── exception/                      # GlobalExceptionHandler
│   │   ├── logging/                        # ChatLog
│   │   └── ratelimit/                      # Bucket4j per-user rate limiting
│   └── src/main/resources/
│       ├── application.properties
│       └── db/migration/                   # Flyway V1–V5
├── admin-ui/                               # React + Vite + shadcn/ui (AspectOR brand)
│   └── src/pages/
│       ├── LoginPage.tsx
│       ├── PlaygroundPage.tsx
│       ├── SettingsPage.tsx                # My Models tab, BYOK, Change Password
│       └── admin/                          # Dashboard, ChatLogs, ModelManager, UserManager, UsageLimits
├── memory/
│   ├── adrs/                               # Architectural Decision Records (ADR-001 – ADR-016)
│   ├── prds/                               # Product Requirements Documents
│   └── learnings/                          # Hard lessons from development
├── docker-compose.yml
├── nginx.conf
├── .env.example
└── CLAUDE.md                               # Full project doc (read by AI sessions)
```

---

## Roadmap

- [x] Phase 1 — Secure nginx proxy
- [x] Phase 2 — Spring Boot JWT gateway, rate limiting, chat logging
- [x] Phase 3 — Full Docker stack (multi-stage builds, health checks)
- [x] Phase 4 — Admin UI + AI Playground (React + shadcn/ui, branded AspectOR)
- [x] Phase 4.5 — SSE streaming, markdown quality, auth context fix, login bug fixes
- [x] Phase 4.6 — BYOK per-user API key (AES-GCM), daily usage tracking, usage limits admin
- [x] Phase 4.7 — User-level model preferences (My Models tab, Playground scoping)
- [x] Phase 4.8 — Model lifecycle: 404 auto-disable, upstream error UX (429/404), V6 migration
- [ ] Phase 4.9 — PRD-004: Auto-sync new free models from OpenRouter (pending)
- [ ] Phase 5 — GitHub Actions CI/CD pipeline

---

*Built as a learning project for Docker, nginx, Spring Boot, and secure API proxy patterns.*

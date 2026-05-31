# AspectOR — Secure OpenRouter Gateway

A self-hosted, secure API gateway for [OpenRouter](https://openrouter.ai) free-tier LLMs.
JWT-authenticated, rate-limited, fully logged, with an admin dashboard and AI playground.

> **Stack:** Docker · nginx · Spring Boot 3.5 (Java 25) · MySQL 8 · React + shadcn/ui  
> **Tested on:** Windows 11 (Docker Desktop + PowerShell)

---

## Architecture

```
Browser (localhost:3000)
  │
  ▼ HTTP :8080
Spring Boot — JWT auth, rate limiting (Bucket4j), chat logging, conversations
  │
  ▼ HTTP :8081 (localhost only)
nginx reverse proxy — token injection (envsubst), TLS to OpenRouter
  │
  ▼ HTTPS
openrouter.ai (free models only)
  │
  ▼
MySQL :3309 — users, chat_logs, conversations, conversation_messages, model_config
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
cp .env.example .env        :: fill in OPENROUTER_API_KEY and secrets

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
| `OPENROUTER_API_KEY` | nginx | Injected at runtime via envsubst |
| `MYSQL_ROOT_PASSWORD` | MySQL | |
| `MYSQL_DATABASE` | MySQL + Spring Boot | `openrouter_gateway` |
| `MYSQL_USER` | MySQL + Spring Boot | |
| `MYSQL_PASSWORD` | MySQL + Spring Boot | |
| `JWT_SECRET` | Spring Boot | Base64-encoded, min 256 bits |
| `JWT_EXPIRATION_MS` | Spring Boot | Default: 86400000 (24h) |
| `OPENROUTER_PROXY_URL` | Spring Boot | `http://localhost:8081` local / `http://openrouter-proxy:8080` Docker |

Generate a JWT secret:
```powershell
powershell -command "[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))"
```

---

## Schema Management

Schema is managed by **Flyway**. Migrations are in `app/src/main/resources/db/migration/`:

| Migration | Content |
|---|---|
| `V1__initial_schema.sql` | All 5 tables |
| `V2__seed_model_config.sql` | 24 free model rows |
| `V3__seed_admin_user.sql` | Default admin user |

Flyway runs automatically on startup. No manual DB setup required.

To reset (development only):
```sql
DROP DATABASE openrouter_gateway;
CREATE DATABASE openrouter_gateway CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
Then restart the app — Flyway re-applies all migrations.

When models change: add a new migration `V4__...sql` — **never edit V1–V3 after they have been applied**.

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
switch-java-version.bat 21
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
GET  /api/conversations
POST /api/conversations
GET  /api/conversations/{id}
POST /api/conversations/{id}/messages
DELETE /api/conversations/{id}
```

### Admin (JWT — ROLE_ADMIN)
```
GET  /api/admin/stats
GET  /api/admin/chat-logs               ?page=0&size=20&user=&model=&from=&to=
GET  /api/admin/chat-logs/export        CSV download
GET  /api/admin/models
PUT  /api/admin/models/{modelId}/toggle
GET  /api/admin/users
PUT  /api/admin/users/{id}/role
PUT  /api/admin/users/{id}/status
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
- All ports bound to `127.0.0.1` only
- Container runs as non-root (`nginx` user, uid 101)
- Root filesystem is read-only (`read_only: true`)
- All Linux capabilities dropped (`cap_drop: ALL`)
- JWT secret enforced min 256 bits at startup (JJWT)
- BCrypt strength 12

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `WeakKeyException` on startup | JWT_SECRET too short | Regenerate — min 32 bytes Base64 |
| nginx restarting | `OPENROUTER_API_KEY` missing | Add key to `.env` |
| `SchemaManagementException: wrong column type [enabled]` | Boolean column type mismatch | Flyway V1 must use `BIT(1)` — Hibernate 6 maps Java `boolean` → `BIT`, not `TINYINT` |
| Flyway checksum mismatch | V1/V2/V3 edited after being applied | Never edit applied migrations — create V4+ instead; drop DB for dev reset |
| Admin login fails | User not seeded | Flyway V3 seeds admin on fresh DB; check `users` table |
| Dark mode not working | Tailwind color format wrong | Use `var()` not `hsl(var())` — radix-nova uses oklch |
| Model switch not working | Conversation model is immutable | Switching model creates a new conversation — by design |
| Empty bubble after 429 | Upstream 429 treated as success | `ConversationController` checks `statusCode >= 400`, rolls back |
| Gradle build fails `version 69` | Gradle running on Java 25 | `switch-java-version.bat 21` first |

---

## Repository Structure

```
secure-openrouter-docker/
├── app/                                    # Spring Boot Java 25
│   └── src/main/resources/
│       ├── application.properties
│       └── db/migration/                   # Flyway migrations
│           ├── V1__initial_schema.sql
│           ├── V2__seed_model_config.sql
│           └── V3__seed_admin_user.sql
├── admin-ui/                               # React + Vite + shadcn/ui (AspectOR brand)
├── db/
│   └── seed.sql                            # DEPRECATED — reference only, not mounted
├── memory/
│   ├── adrs/                               # Architectural Decision Records
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
- [ ] Phase 5 — GitHub Actions CI/CD pipeline

---

*Built as a learning project for Docker, nginx, Spring Boot, and secure API proxy patterns.*

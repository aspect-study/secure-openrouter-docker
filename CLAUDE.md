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
switch-java-version.bat 21
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
```

### System
```
GET /actuator/health                 Spring Boot health (public)
GET http://localhost:8081/health     nginx proxy health
```

---

## Database Schema

Tables managed by Hibernate `ddl-auto=update` except `model_config` which is seeded via `db/seed.sql`:

- `users` — email, password_hash (BCrypt), role (USER/ADMIN), active, timestamps
- `chat_logs` — per-request log: user, model, tokens, latency, status, response preview
- `model_config` — enabled/disabled state per free model, seeded from `db/seed.sql`
- `conversations` — per-user chat sessions: title, model, timestamps
- `conversation_messages` — messages per conversation: role (user/assistant), content

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
- **Never expose ports on 0.0.0.0** — all ports bind to `127.0.0.1` only
- **Gradle wrapper only** — never run `gradle` directly; always use `gradlew.bat`
- **Gradle runtime must be Java 21** — Gradle 8.14 does not support Java 25 runtime (ADR-004)
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
| Gradle build fails `version 69` | Running Gradle on Java 25 | `switch-java-version.bat 21` first |
| `outline-ring/50` CSS error | shadcn radix-nova opacity modifier incompatible | Remove `outline-ring/50` from index.css |

---

## Roadmap

- [x] Phase 1 — Secure nginx proxy prototype
- [x] Phase 2 — Spring Boot JWT gateway, rate limiting, chat logging
- [x] Phase 3 — Dockerize Spring Boot (multi-stage Dockerfile)
- [x] Phase 4 — Admin UI + AI Playground (React + shadcn/ui)
- [x] Phase 4.5 — SSE streaming, markdown quality, auth context fix, login bug fixes
- [ ] Phase 5 — GitHub Actions CI/CD pipeline

# PRD-001: Secure OpenRouter Gateway

**Created:** 2026-05-29
**Author:** aspect (aspectjump.java@gmail.com)
**Status:** Phase 1 ✅ Phase 2 ✅ Phase 3 ✅ Phase 4 ✅ Phase 5 pending

---

## Problem Statement

OpenRouter provides access to free LLM models via API, but using it directly from any app or script means:
- The API token is spread across multiple projects/scripts
- No audit trail of who called what model and when
- No rate limiting — a single runaway script can exhaust the free tier
- No security layer — any code with the token can call paid models
- No UI for non-developer users to explore models

## Goal

Build a self-hosted, secure API gateway for OpenRouter that:
1. Centralizes token management (one place, never in code)
2. Enforces authentication (only registered users can call the API)
3. Limits free model usage per user
4. Logs every request for usage analysis
5. Restricts access to free models only — no accidental paid model calls
6. Provides an admin dashboard for monitoring and management
7. Provides an AI Playground UI for interactive model exploration

---

## Phases

### Phase 1 — nginx Proxy Prototype ✅ Complete

**What was built:**
- nginx reverse proxy in Docker (Alpine, non-root)
- Token injection via `envsubst` at container startup
- TLS verification to `openrouter.ai`
- Localhost-only port binding (`127.0.0.1:8081`)
- Read-only container filesystem, dropped Linux capabilities
- Health check endpoint
- Test scripts for PowerShell and Bash

**Acceptance criteria met:**
- Container starts healthy
- `GET /health` returns `{"status":"ok"}`
- `POST /api/v1/chat/completions` with free model returns LLM response
- API key never appears in image layers or source control

---

### Phase 2 — Spring Boot Gateway ✅ Complete

**What was built:**
- Spring Boot 3.5, Java 25 toolchain, Gradle Groovy DSL
- JWT authentication (register, login, Bearer token on all chat endpoints)
- Per-user rate limiting via Bucket4j (10 req/min, configurable)
- Free model whitelist enforced server-side (24 verified models)
- Every request logged to MySQL (`chat_logs` table)
- Virtual threads enabled (`spring.threads.virtual.enabled=true`)
- Global exception handler with clean JSON error responses
- Dedicated MySQL 8.0 container (`openrouter_gateway` database, port 3309)

**Acceptance criteria met:**
- `POST /api/auth/register` creates user with BCrypt-hashed password
- `POST /api/auth/login` returns signed JWT
- `POST /api/chat/completions` proxies to OpenRouter via nginx
- Rate limit returns 429 with `Retry-After` header
- `chat_logs` table populated after each request
- Disallowed model returns 400 with descriptive error

---

### Phase 3 — Full Dockerization ✅ Complete

**What was built:**
- `app/Dockerfile` — multi-stage build (Java 21 Gradle → Java 25 JRE Alpine runtime)
- Non-root `appuser` in container
- Health check on `/actuator/health`
- Updated `docker-compose.yml` — all 4 services with `depends_on` health conditions
- `OPENROUTER_PROXY_URL` switches from `localhost:8081` → `openrouter-proxy:8080` via environment override
- `db/seed.sql` mounts as Docker init script for first-run setup

**Acceptance criteria met:**
- `docker compose up -d` brings entire stack up
- All services show (healthy) after startup
- Spring Boot correctly connects to MySQL and nginx via Docker network names

---

### Phase 4 — Admin UI + AI Playground ✅ Complete

**What was built:**

**AI Playground (`/playground`):**
- Chat interface with conversation persistence (MySQL)
- Model selector via `Ctrl+K` command palette — keyboard navigable
- Per-model descriptions: best use, limitations, context window, rate limit
- Real token usage display (from OpenRouter response, not estimated)
- Auto-titling of conversations from first message
- Typing indicator with wave animation
- Responsive layout (mobile drawer, desktop sidebar)
- Dark/light theme toggle (persistent via localStorage)

**Admin Dashboard (`/admin/*`):**
- Dashboard — stat cards + 7-day requests chart
- Chat Logs — paginated table with filters + CSV export
- Model Manager — enable/disable models per toggle (persisted to `model_config`)
- User Manager — role changes, activate/deactivate accounts
- All routes protected by `ROLE_ADMIN`

**Tech stack:**
- Vite + React + TypeScript
- TailwindCSS v3 + shadcn/ui (radix-nova style)
- Lucide React icons
- Axios with JWT interceptor + 401 auto-logout
- React Router v6

**New backend:**
- `AdminController.java` — all admin endpoints
- `ModelConfig.java` + `ModelConfigRepository.java`
- `Conversation.java` + `ConversationMessage.java` + `ConversationController.java`
- Updated `User.java` (added `active` field)
- Updated `JwtAuthFilter` (rejects inactive users)
- Updated `OpenRouterClient` (checks `model_config.enabled`)
- CORS configured for `localhost:3000`

**Acceptance criteria met:**
- Login routes correctly to Playground (USER) or Admin Dashboard (ADMIN)
- Chat conversations persist across page refresh
- Model toggle in admin immediately blocks/allows model in chat
- Deactivated user's JWT is rejected on next request
- All admin tables load real data from DB

**Post-phase additions (same release):**
- Sign Up tab on login page — open registration, auto-login, password strength bar
- Change Password dialog — accessible from both sidebars via 🔑 icon
- Markdown rendering with syntax highlighting (`react-markdown` + `react-syntax-highlighter`)
- 429 / upstream error handling — rolls back user message, restores input, shows contextual toast
- Model switch fix — switching model creates a new conversation to avoid model mismatch
- Real token usage display from OpenRouter `usage` response object
- Brand renamed to **AspectOR** throughout the UI
- Full design overhaul — warm off-white light mode, Claude-style dark mode (warm charcoal + coral-orange primary)
- Per-model descriptions with 5W's, strengths, limitations in Model Manager
- Compact model info in command palette (use case, limitation, context, RPM per model)

---

### Phase 5 — CI/CD Pipeline 🔲 Pending

**Goal:** Automated build, test, and Docker image push on every commit.

**Requirements:**
- GitHub Actions workflow
- Run `gradlew test` on PR
- Build Docker images and push to GHCR on merge to `main`
- Environment secrets managed via GitHub repository secrets
- Cache Gradle dependencies between runs

---

## Non-Functional Requirements

| Requirement | Target | Status |
|---|---|---|
| Spring Boot startup time | < 10 seconds | ✅ ~8s |
| Proxy overhead | < 50ms | ✅ ~1ms |
| Rate limit | 10 req/min per user | ✅ Configurable |
| JWT expiry | 24 hours | ✅ Configurable |
| Password hashing | BCrypt strength 12 | ✅ |
| Port exposure | localhost only | ✅ All 127.0.0.1 |
| Secrets | Never in source control | ✅ |
| Mobile UI | Responsive | ✅ Drawer pattern |

---

## Out of Scope (all phases)

- Multi-tenant organization support
- Billing or paid model access
- OAuth2 / social login
- Horizontal scaling (single-instance only)
- Streaming SSE responses
- Image input (vision models partially supported via API but not UI)

# PRD-001: Secure OpenRouter Gateway

**Created:** 2026-05-29
**Author:** aspect (oliver@lapapa88.com)
**Status:** Phase 1 ✅ Phase 2 ✅ Phase 3 pending

---

## Problem Statement

OpenRouter provides access to free LLM models via API, but using it directly from any app or script means:
- The API token is spread across multiple projects/scripts
- No audit trail of who called what model and when
- No rate limiting — a single runaway script can exhaust the free tier
- No security layer — any code with the token can call paid models

## Goal

Build a self-hosted, secure API gateway for OpenRouter that:
1. Centralizes token management (one place, never in code)
2. Enforces authentication (only registered users can call the API)
3. Limits free model usage per user
4. Logs every request for usage analysis
5. Restricts access to free models only — no accidental paid model calls

---

## Phases

### Phase 1 — nginx Proxy Prototype ✅ Complete

**What was built:**
- nginx reverse proxy running in Docker (Alpine, non-root)
- Injects OpenRouter API token via `envsubst` at container startup
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
- Spring Boot 3.5 app (Java 25 toolchain, Gradle Groovy DSL)
- JWT authentication (register, login, Bearer token on all chat endpoints)
- Per-user rate limiting via Bucket4j (10 req/min, configurable)
- Free model whitelist enforced server-side
- Every request logged to MySQL (`chat_logs` table)
- Virtual threads enabled for efficient blocking I/O
- Global exception handler with clean JSON error responses
- Dedicated MySQL 8.0 container (`openrouter_gateway` database)

**Acceptance criteria met:**
- `POST /api/auth/register` creates user with BCrypt-hashed password
- `POST /api/auth/login` returns signed JWT
- `POST /api/chat/completions` with valid JWT proxies to OpenRouter via nginx
- Rate limit returns 429 with `Retry-After` header when exceeded
- `chat_logs` table populated after each request
- Disallowed model returns 400 with descriptive error

---

### Phase 3 — Full Dockerization 🔲 Pending

**Goal:** Run all three services (nginx, Spring Boot, MySQL) via `docker compose up -d`.

**Requirements:**
- Spring Boot `Dockerfile` using multi-stage build (Gradle build → JRE runtime)
- Base image: `eclipse-temurin:25-jre-alpine`
- Non-root user in container
- `depends_on` with `service_healthy` for MySQL and nginx
- `OPENROUTER_PROXY_URL` switches from `localhost:8081` to `openrouter-proxy:8080` (internal network)
- All secrets via `env_file: .env`
- Single `docker compose up -d` starts the entire stack

**Out of scope for Phase 3:**
- External TLS (handle in Phase 4 with Caddy or nginx TLS terminator)
- Kubernetes deployment

---

### Phase 4 — Admin UI 🔲 Future

**Goal:** A simple web dashboard for monitoring usage.

**Requirements:**
- View per-user token consumption and request counts
- List recent chat logs with model, latency, and response preview
- Manage allowed models whitelist without code changes
- Technology: Thymeleaf + Spring MVC (keep stack consistent) or React SPA

---

### Phase 5 — CI/CD Pipeline 🔲 Future

**Goal:** Automated build, test, and Docker image push on every commit.

**Requirements:**
- GitHub Actions workflow
- Run `gradlew test` on PR
- Build Docker image and push to GHCR on merge to `main`
- Environment secrets managed via GitHub repository secrets

---

## Non-Functional Requirements

| Requirement | Target |
|---|---|
| Startup time | < 10 seconds |
| Request latency (proxy overhead) | < 50ms added over direct OpenRouter call |
| Rate limit | 10 requests/minute per user (configurable) |
| JWT expiry | 24 hours (configurable) |
| Password hashing | BCrypt strength 12 |
| Port exposure | localhost only (no 0.0.0.0 bindings) |
| Secrets | Never in source control, never in image layers |

---

## Out of Scope (all phases)

- Multi-tenant organization support
- Billing or paid model access
- Streaming (SSE) response support — proxy buffering is off, but Spring Boot response handling is not streaming-aware yet
- OAuth2 / social login
- Horizontal scaling (single-instance only for now)

# CLAUDE.md — Secure OpenRouter Gateway

Read this before touching any code.

---

## Project Overview

A secure, local API gateway for OpenRouter's free LLM models.
Three-layer architecture: nginx reverse proxy → Spring Boot backend → MySQL.

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
├── app/            # Spring Boot Java 25 application
├── admin-ui/       # React + Vite + shadcn/ui admin dashboard (branded: AspectOR)
├── db/             # DEPRECATED seed.sql — schema now owned by Flyway
├── docs/           # Reference documentation (see Reference Docs below)
├── memory/         # ADRs, PRDs, and dev learnings
├── Dockerfile      # nginx proxy image
├── docker-compose.yml
├── nginx.conf      # Hardened nginx config
├── docker-entrypoint.sh   # envsubst token injection
├── .env.example    # Template — copy to .env, never commit .env
└── run-app.bat     # Loads .env + starts Spring Boot locally
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
run-app.bat
```
Loads `.env`, switches to Java 21, and runs `gradlew.bat bootRun`.

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

### Run full stack in Docker
```cmd
docker compose up -d
docker compose ps   # wait for all 4 (healthy)
```

---

## Default Admin Credentials

```
Email:    admin@openrouter.local
Password: Admin@2026!
```
**Change this password after first login.**

---

## Reference Docs

| File | When to use |
|---|---|
| [`docs/environment.md`](docs/environment.md) | Setting up `.env`, generating secrets, understanding env var requirements |
| [`docs/api-reference.md`](docs/api-reference.md) | Working on any endpoint, SSE protocol, request/response shapes |
| [`docs/schema.md`](docs/schema.md) | DB table structure, Flyway migration history, database invariants |
| [`docs/constraints.md`](docs/constraints.md) | Before making any change — rules, gotchas, and symptom→fix by subsystem |

---

## Roadmap

- [x] Phase 1 — Secure nginx proxy prototype
- [x] Phase 2 — Spring Boot JWT gateway, rate limiting, chat logging
- [x] Phase 3 — Dockerize Spring Boot (multi-stage Dockerfile)
- [x] Phase 4 — Admin UI + AI Playground (React + shadcn/ui)
- [x] Phase 4.5 — SSE streaming, markdown quality, auth context fix, login bug fixes
- [x] Phase 4.6 — PRD-002: BYOK, AES-GCM encryption, daily usage tracking, usage limits admin
- [x] Phase 4.7 — PRD-003: User-level model preferences (My Models tab, Playground scoping)
- [x] Phase 4.8 — Model lifecycle: 404 auto-disable, V6 migration, upstream error UX
- [x] Phase 4.9 — PRD-004: Auto-sync new free models from OpenRouter (startup + on-demand)
- [x] Phase 5.0 — PRD-005: Gateway Intelligence Agent (ReAct agent, two tools)
- [ ] Phase 5.1 — GitHub Actions CI/CD pipeline

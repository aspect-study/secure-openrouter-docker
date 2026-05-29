# CLAUDE.md — Secure OpenRouter Gateway

This file is the entry point for any AI session or new developer working on this project.
Read this before touching any code.

---

## Project Overview

A secure, local API gateway for OpenRouter's free LLM models.
Three-layer architecture: nginx reverse proxy → Spring Boot backend → MySQL.

```
Client
  │
  ▼ HTTP :8080
Spring Boot (JWT auth, rate limiting, logging)
  │
  ▼ HTTP :8081 (localhost only)
nginx reverse proxy (token injection, TLS to OpenRouter)
  │
  ▼ HTTPS
openrouter.ai (free models only)
  │
  ▼
MySQL :3309 (users, chat_logs)
```

---

## Repository Structure

```
secure-openrouter-docker/
├── app/                          # Spring Boot Java 25 application
│   ├── src/main/java/com/openrouter/gateway/
│   │   ├── auth/                 # JWT, User entity, register/login
│   │   ├── chat/                 # ChatController, ChatService, OpenRouterClient
│   │   ├── config/               # SecurityConfig, HttpClientConfig, AppProperties
│   │   ├── exception/            # GlobalExceptionHandler
│   │   ├── logging/              # ChatLog entity + repository
│   │   └── ratelimit/            # Bucket4j per-user rate limiting
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── build.gradle              # Groovy DSL (NOT Kotlin DSL — see ADR-003)
│   ├── settings.gradle
│   └── gradlew.bat               # Always use the wrapper, never global gradle
├── Dockerfile                    # nginx proxy image
├── docker-compose.yml            # nginx + MySQL services (Spring Boot Phase 3)
├── nginx.conf                    # Hardened nginx config
├── docker-entrypoint.sh          # Injects token via envsubst at runtime
├── .env.example                  # Template — copy to .env, never commit .env
├── run-app.bat                   # Loads .env + starts Spring Boot locally
├── test-request.ps1              # PowerShell smoke test for nginx proxy
├── test-request.sh               # Bash smoke test for nginx proxy
├── memory/
│   ├── adrs/                     # Architectural Decision Records
│   └── prds/                     # Product Requirements Documents
└── logs/                         # nginx access/error logs (gitignored)
```

---

## Build Commands

### Prerequisites
- Docker Desktop running
- Java 21 in PATH for running Gradle (see ADR-004)
- Java 25 installed at `C:\Program Files\Java\jdk-25`

### Start infrastructure (nginx proxy + MySQL)
```cmd
docker compose up -d
docker compose ps   # verify both show (healthy)
```

### Run Spring Boot locally
```cmd
cd C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker
run-app.bat
```
This loads `.env`, switches to Java 21, and runs `gradlew.bat bootRun`.

### Build Spring Boot (no tests)
```cmd
cd app
switch-java-version.bat 21
gradlew.bat build -x test
```

### Build Spring Boot (with tests)
```cmd
cd app
switch-java-version.bat 21
gradlew.bat test
```

### Switch Java versions (CMD only)
```cmd
switch-java-version.bat 21   # for Gradle
switch-java-version.bat 25   # for running the app directly
switch-java-version.bat 8    # legacy
```

---

## Environment Variables

All secrets live in `.env` (never committed). Copy from `.env.example`.

| Variable | Used by | Notes |
|---|---|---|
| `OPENROUTER_API_KEY` | nginx | Injected at container startup via envsubst |
| `DEFAULT_MODEL` | test scripts | Default free model for smoke tests |
| `MYSQL_ROOT_PASSWORD` | MySQL container | Rarely used directly |
| `MYSQL_DATABASE` | MySQL + Spring Boot | Database name: `openrouter_gateway` |
| `MYSQL_USER` | MySQL + Spring Boot | App-level DB user |
| `MYSQL_PASSWORD` | MySQL + Spring Boot | App-level DB password |
| `JWT_SECRET` | Spring Boot | Base64-encoded, minimum 256 bits (32 bytes) |
| `JWT_EXPIRATION_MS` | Spring Boot | Default: 86400000 (24 hours) |
| `OPENROUTER_PROXY_URL` | Spring Boot | Default: http://localhost:8081 |

---

## API Endpoints

### Auth (public)
```
POST /api/auth/register   {"email": "...", "password": "..."}
POST /api/auth/login      {"email": "...", "password": "..."}
```

### Chat (requires JWT)
```
POST /api/chat/completions   {"model": "...", "messages": [...]}
GET  /api/chat/models        Returns allowed free model list
```

### System
```
GET /actuator/health         Spring Boot health (public)
GET http://localhost:8081/health   nginx proxy health
```

---

## Allowed Free Models

```
nvidia/nemotron-nano-9b-v2:free
meta-llama/llama-3.3-70b-instruct:free
meta-llama/llama-3.2-3b-instruct:free
deepseek/deepseek-v4-flash:free
qwen/qwen3-coder:free
nousresearch/hermes-3-llama-3.1-405b:free
```

Update in `OpenRouterClient.java` (FREE_MODELS set) when models change.
Verify current free models: https://openrouter.ai/models?max_price=0

---

## Key Constraints

- **Never hardcode the OpenRouter API key** — it lives only in `.env` and is injected by `docker-entrypoint.sh` at runtime via `envsubst`
- **Never expose ports on 0.0.0.0** — all ports bind to `127.0.0.1` only
- **Gradle wrapper only** — never run `gradle` directly; always use `gradlew.bat`
- **Gradle runtime must be Java 21** — Gradle 8.14 does not support Java 25 as runtime (see ADR-004)
- **JWT secret must be Base64-encoded and ≥ 256 bits** — JJWT enforces this at startup
- **MySQL port is 3309** — 3306 and 3307 are taken by other local instances
- **nginx proxy port is 8081** — 8080 is used by Spring Boot locally

---

## Troubleshooting Quick Reference

| Symptom | Likely cause | Fix |
|---|---|---|
| `WeakKeyException` on startup | JWT_SECRET too short | Regenerate with 32+ random bytes, Base64-encode |
| nginx container restarting | `OPENROUTER_API_KEY` missing in `.env` | Add key to `.env` |
| Port conflict on 8081 | Spring Boot running on 8080, something else on 8081 | Check `netstat -ano` |
| `No endpoints found` from OpenRouter | Model removed from free tier | Check https://openrouter.ai/models?max_price=0 |
| `429 rate limited` from OpenRouter | Free tier upstream throttle | Wait 30s, try different model |
| Gradle build fails with `version 69` | Running Gradle on Java 25 | `switch-java-version.bat 21` first |

---

## Next Steps (Roadmap)

- [ ] Phase 3 — Dockerize Spring Boot, wire all three services in docker-compose
- [ ] Phase 4 — Admin UI (model usage dashboard, user management)
- [ ] Phase 5 — GitHub Actions CI/CD pipeline

# Secure OpenRouter Docker Proxy

A hardened, local reverse proxy that forwards requests to the [OpenRouter](https://openrouter.ai) API using only **free models** — no paid model access required. Built with nginx on Alpine Linux, running as a non-root user inside Docker.

> **Stack:** Docker · nginx (Alpine) · OpenRouter free-tier models  
> **Tested on:** Windows 11 (Docker Desktop + PowerShell) · macOS Ventura/Sonoma (Docker Desktop + Zsh)

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Docker Desktop Installation](#docker-desktop-installation)
4. [Project Setup](#project-setup)
5. [Environment Variables](#environment-variables)
6. [Free Models Reference](#free-models-reference)
7. [Build & Run](#build--run)
8. [Verify & Test](#verify--test)
9. [Security Practices](#security-practices)
10. [TLS / HTTPS Setup](#tls--https-setup)
11. [Troubleshooting](#troubleshooting)
12. [Version Control Best Practices](#version-control-best-practices)
13. [Roadmap](#roadmap)

---

## Architecture Overview

```
Your App / curl / Postman
         │
         ▼  HTTP  (localhost:8081)
┌─────────────────────────┐
│  Docker Container       │
│  nginx (non-root)       │  ◄── Token injected at runtime via env var
│  /api/v1/* → proxy      │
└──────────┬──────────────┘
           │  HTTPS (TLS verified)
           ▼
    openrouter.ai/api/v1
           │
           ▼
   Free LLM (Mistral, Gemma, etc.)
```

The proxy:
- Strips any `Authorization` header from the caller and replaces it with your token.
- Validates the TLS certificate of `openrouter.ai` (no MITM risk).
- Never bakes your token into the image — it is injected only at container startup.

---

## Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Docker Desktop | 4.x | Includes Docker Compose v2 |
| curl | Any | For smoke tests (built into macOS/Linux; install via choco on Windows) |
| Python 3 | Any | Optional — for pretty-printing JSON in test script |

---

## Docker Desktop Installation

### Windows (PowerShell — run as Administrator)

```powershell
# Option A: winget (Windows 10 21H2+ / Windows 11)
winget install -e --id Docker.DockerDesktop

# Option B: chocolatey
choco install docker-desktop -y

# After install, restart your machine, then verify:
docker --version
docker compose version
```

Enable WSL 2 backend (recommended):
1. Open Docker Desktop → Settings → General → tick **"Use the WSL 2 based engine"**
2. Settings → Resources → WSL Integration → enable your distro

### macOS (Bash/Zsh)

```bash
# Option A: Homebrew (recommended)
brew install --cask docker

# Option B: Download the .dmg directly
# https://www.docker.com/products/docker-desktop/

# After install, open Docker Desktop from Applications, then verify:
docker --version
docker compose version
```

> **Apple Silicon (M1/M2/M3):** Docker Desktop ships a native ARM64 image. No Rosetta needed.

---

## Project Setup

### Clone the repository

```bash
# macOS / Linux / Git Bash on Windows
git clone https://github.com/YOUR_USERNAME/secure-openrouter-docker.git
cd secure-openrouter-docker
```

```powershell
# Windows PowerShell
git clone https://github.com/YOUR_USERNAME/secure-openrouter-docker.git
Set-Location secure-openrouter-docker
```

### Create the logs directory

```bash
# macOS/Linux
mkdir -p logs
chmod 700 logs        # restrict access — logs may contain request data
```

```powershell
# Windows PowerShell
New-Item -ItemType Directory -Force -Path logs
# Windows NTFS permissions (restrict to current user only):
icacls logs /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F"
```

---

## Environment Variables

Copy the example file and fill in your token:

```bash
# macOS/Linux
cp .env.example .env
```

```powershell
# Windows PowerShell
Copy-Item .env.example .env
```

Edit `.env`:

```dotenv
OPENROUTER_API_KEY=sk-or-v1-YOUR_REAL_KEY_HERE
DEFAULT_MODEL=mistralai/mistral-7b-instruct:free
```

Get your free API key at **https://openrouter.ai/keys**.

> ⚠️ **Never commit `.env`** — it is already in `.gitignore`. Confirm with `git status` before every push.

### Platform differences

| Platform | Variable expansion | Notes |
|---|---|---|
| macOS/Linux | `$OPENROUTER_API_KEY` | Standard POSIX |
| Windows CMD | `%OPENROUTER_API_KEY%` | Legacy |
| Windows PowerShell | `$env:OPENROUTER_API_KEY` | Use this in scripts |

---

## Free Models Reference

The following models have a `$0/token` price on OpenRouter (verify current availability at https://openrouter.ai/models?max_price=0):

| Model ID | Provider | Context | Notes |
|---|---|---|---|
| `mistralai/mistral-7b-instruct:free` | Mistral AI | 32k | Best general-purpose free model |
| `google/gemma-3-1b-it:free` | Google | 32k | Lightweight, fast |
| `google/gemma-3-4b-it:free` | Google | 128k | Better quality |
| `google/gemma-3-12b-it:free` | Google | 96k | High quality |
| `meta-llama/llama-3.2-3b-instruct:free` | Meta | 131k | Great for code |
| `meta-llama/llama-3.1-8b-instruct:free` | Meta | 131k | Strong reasoning |
| `deepseek/deepseek-r1-0528:free` | DeepSeek | 163k | Excellent reasoning |
| `qwen/qwen3-8b:free` | Alibaba | 131k | Multilingual |

> Free models may have rate limits and slower response times than paid tiers. If a request fails with 429, wait and retry.

---

## Build & Run

### Build the image

```bash
# macOS/Linux
docker compose build
```

```powershell
# Windows PowerShell
docker compose build
```

### Start the container

```bash
# macOS/Linux — detached (background)
docker compose up -d

# View real-time logs
docker compose logs -f
```

```powershell
# Windows PowerShell — detached
docker compose up -d

# View logs
docker compose logs -f
```

### Stop and remove

```bash
docker compose down
```

### Rebuild after config changes

```bash
docker compose down && docker compose build --no-cache && docker compose up -d
```

---

## Verify & Test

### 1. Check the container is running

```bash
docker compose ps
```

Expected output — `STATUS` should be `Up` and `(healthy)`:

```
NAME                IMAGE                       STATUS
openrouter-proxy    secure-openrouter-docker    Up 30 seconds (healthy)
```

### 2. Hit the health endpoint

```bash
# macOS/Linux
curl -s http://localhost:8081/health

# Windows PowerShell
Invoke-RestMethod http://localhost:8081/health
```

Expected: `{"status":"ok"}`

### 3. Send a chat completion to a free model

```bash
# macOS/Linux
./test-request.sh
```

```powershell
# Windows PowerShell
.\test-request.ps1
```

Or manually with curl:

```bash
curl -s -X POST http://localhost:8081/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "mistralai/mistral-7b-instruct:free",
    "messages": [{"role": "user", "content": "Say hello in one sentence."}]
  }' | python3 -m json.tool
```

```powershell
# Windows PowerShell equivalent
$body = @{
  model    = "mistralai/mistral-7b-instruct:free"
  messages = @(@{ role = "user"; content = "Say hello in one sentence." })
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/chat/completions" `
  -Method POST -ContentType "application/json" -Body $body
```

A successful response looks like:

```json
{
  "id": "gen-...",
  "model": "mistralai/mistral-7b-instruct:free",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "Hello! I hope you're having a wonderful day."
      }
    }
  ],
  "usage": { "prompt_tokens": 14, "completion_tokens": 14 }
}
```

### 4. List available models via the proxy

```bash
curl -s http://localhost:8081/api/v1/models | python3 -m json.tool | grep '"id"' | head -20
```

---

## Security Practices

### Secrets management

- **Never hardcode** the API key in `Dockerfile`, `docker-compose.yml`, or any source file.
- The key is read from `.env` at runtime and written to an nginx config snippet **inside the container** by `docker-entrypoint.sh` — it never appears in the image layers.
- Rotate your OpenRouter key immediately if you accidentally commit it. Revoke it at https://openrouter.ai/keys.

```bash
# Scan for accidentally committed secrets before pushing
git log --all --full-history -- .env
git diff HEAD~1 HEAD -- .env
```

### Non-root container

The container runs as the `nginx` user (uid 101). Verify:

```bash
docker compose exec openrouter-proxy whoami
# Expected: nginx
```

### Port binding

The port is bound to `127.0.0.1:8080` only — not `0.0.0.0`. This means it is **not reachable from other machines on your network** by default.

```yaml
# docker-compose.yml (correct — localhost only)
ports:
  - "127.0.0.1:8080:8080"

# WRONG — exposes to all interfaces, never do this for a proxy with a token
# ports:
#   - "8080:8080"
```

### Read-only filesystem

The container root filesystem is mounted read-only (`read_only: true`). Only `tmpfs` mounts (cache, pid, tmp) and the bind-mounted `./logs` are writable. This prevents any write-based exploits from persisting.

### Capability dropping

```yaml
cap_drop:
  - ALL
security_opt:
  - no-new-privileges:true
```

nginx on a non-privileged port (8080) needs no Linux capabilities.

### Log security

Nginx access logs record full request paths. Treat them as sensitive:

```bash
# After first run, restrict log directory permissions
chmod 700 ./logs          # macOS/Linux
# Windows:
icacls logs /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F"
```

Do not ship raw nginx logs to a public log aggregator without scrubbing Authorization headers. The proxy config already strips the caller's `Authorization` header before logging.

### Firewall (host-level)

On Linux hosts, use `ufw` or `iptables` to restrict port 8080 to localhost if running outside Docker Desktop:

```bash
sudo ufw allow from 127.0.0.1 to any port 8080
sudo ufw deny 8080
```

On Windows, the `127.0.0.1` bind in docker-compose already handles this for Docker Desktop setups.

---

## TLS / HTTPS Setup

The proxy itself communicates with `openrouter.ai` over verified HTTPS. If you want to expose the proxy beyond localhost (e.g., to a LAN or the internet), add TLS termination **in front** of this container.

### Option A: Caddy (simplest — auto TLS)

```yaml
# Add to docker-compose.yml
  caddy:
    image: caddy:alpine
    ports:
      - "443:443"
      - "80:80"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
    networks:
      - proxy-net

volumes:
  caddy_data:
```

```
# Caddyfile
your.domain.com {
    reverse_proxy openrouter-proxy:8080
}
```

### Option B: nginx with Let's Encrypt (certbot)

Use the standard `nginx` + `certbot` pattern. Point nginx to `proxy_pass http://openrouter-proxy:8080` after TLS termination.

> **Never expose the proxy on port 80/443 without TLS if it holds an API token.**

---

## Troubleshooting

### Container exits immediately

```bash
docker compose logs openrouter-proxy
```

**Most common cause:** `OPENROUTER_API_KEY` is missing or empty in `.env`.

```
[ERROR] OPENROUTER_API_KEY is not set. Aborting.
```

Fix: verify `.env` contains the key with no trailing spaces.

---

### `connection refused` on port 8080

```bash
# Check container is actually running
docker compose ps

# Check port binding
docker port openrouter-proxy
```

On Windows with WSL 2, ensure Docker Desktop is running and the WSL 2 engine is enabled. Try restarting Docker Desktop.

---

### `502 Bad Gateway`

nginx reached OpenRouter but got an error back. Check:

```bash
docker compose logs openrouter-proxy
cat logs/error.log
```

- OpenRouter may be rate-limiting you (free tier). Wait 60 seconds and retry.
- The model ID may be wrong or no longer free. Check https://openrouter.ai/models?max_price=0.

---

### `401 Unauthorized` from OpenRouter

Your API token is invalid or expired.

```bash
# Test the token directly (bypass the proxy)
curl -s https://openrouter.ai/api/v1/models \
  -H "Authorization: Bearer $OPENROUTER_API_KEY" | python3 -m json.tool | head -20
```

If this also returns 401, regenerate your key at https://openrouter.ai/keys.

---

### `413 Request Entity Too Large`

The request body exceeds nginx's `client_max_body_size` (default 2m in this config). Increase it in `nginx.conf`:

```nginx
client_max_body_size 10m;
```

Then rebuild: `docker compose build --no-cache && docker compose up -d`

---

### SSL/TLS errors in nginx logs

```
SSL_do_handshake() failed ... peer closed connection
```

The Alpine base image ships CA certificates. If you see TLS errors, update them:

```dockerfile
# Add to Dockerfile before the USER directive
RUN apk add --no-cache ca-certificates && update-ca-certificates
```

---

### macOS: `docker: command not found` after install

Docker Desktop on macOS adds its CLI to `/usr/local/bin`. If it's missing from `$PATH`:

```zsh
export PATH="/usr/local/bin:$PATH"
# Add to ~/.zshrc for persistence
echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

### Windows: PowerShell execution policy error

```powershell
# Allow running local scripts (current user only — safe)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

### Port already in use

```bash
# macOS/Linux — find what's using 8080
lsof -i :8080

# Windows PowerShell
netstat -ano | Select-String "8080"
Get-Process -Id (netstat -ano | Select-String "8080" | ForEach-Object { ($_ -split '\s+')[-1] } | Select-Object -First 1)
```

Change the host port in `docker-compose.yml` if needed:
```yaml
ports:
  - "127.0.0.1:9090:8080"   # use 9090 on the host instead
```

---

## Version Control Best Practices

### Repository initialization

```bash
git init
git add .
git status           # verify .env is NOT listed
git commit -m "feat: initial secure OpenRouter proxy setup"
```

### Pre-commit checklist

Before every `git push`:

```bash
# 1. Confirm .env is not tracked
git ls-files | grep -i ".env$"   # should return nothing

# 2. Scan for leaked tokens
git diff --cached | grep -i "sk-or"  # should return nothing

# 3. Review what you're committing
git diff --cached --stat
```

### Recommended branch strategy

```
main          — stable, deployable
dev           — active development
feat/*        — feature branches
fix/*         — bug fix branches
```

```bash
git checkout -b feat/add-spring-boot-backend
# ... work ...
git push -u origin feat/add-spring-boot-backend
# Open a pull request — review before merging to main
```

### What to commit vs. what to exclude

| File | Commit? | Reason |
|------|---------|--------|
| `Dockerfile` | ✅ Yes | No secrets |
| `docker-compose.yml` | ✅ Yes | No secrets (uses env_file) |
| `nginx.conf` | ✅ Yes | No secrets |
| `docker-entrypoint.sh` | ✅ Yes | No secrets |
| `.env.example` | ✅ Yes | Template only |
| `.gitignore` | ✅ Yes | Meta |
| `README.md` | ✅ Yes | Documentation |
| `.env` | ❌ No | Contains real token |
| `logs/` | ❌ No | May contain request data |

### Tagging releases

```bash
git tag -a v0.1.0 -m "Initial proxy prototype"
git push origin v0.1.0
```

---

## Roadmap

- [x] Phase 1 — Secure nginx proxy prototype (this repo)
- [ ] Phase 2 — Spring Boot (Java 21+) backend with virtual threads, rate limiting (Bucket4j), JWT auth
- [ ] Phase 3 — MySQL integration for request logging / usage tracking
- [ ] Phase 4 — Admin UI (React or Thymeleaf) — model selector, usage dashboard
- [ ] Phase 5 — GitHub Actions CI/CD pipeline

---

## License

MIT — see [LICENSE](LICENSE)

---

*Built as a learning project for Docker, nginx, and secure API proxy patterns.*

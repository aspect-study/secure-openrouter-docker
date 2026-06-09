# Environment Variables

All secrets live in `.env` (never committed). Copy from `.env.example`.

## Variables

| Variable | Used by | Notes |
|---|---|---|
| `OPENROUTER_API_KEY` | nginx + Spring Boot | nginx: injected at startup via envsubst. Spring Boot: optional, used by `FreeModelSyncService` to fetch the OpenRouter models list (public endpoint works without it) |
| `DEFAULT_MODEL` | test scripts | Default free model for smoke tests |
| `MYSQL_ROOT_PASSWORD` | MySQL container | Rarely used directly |
| `MYSQL_DATABASE` | MySQL + Spring Boot | `openrouter_gateway` |
| `MYSQL_USER` | MySQL + Spring Boot | App-level DB user |
| `MYSQL_PASSWORD` | MySQL + Spring Boot | App-level DB password |
| `JWT_SECRET` | Spring Boot | Base64-encoded, minimum 256 bits (32 bytes) |
| `JWT_EXPIRATION_MS` | Spring Boot | Default: 86400000 (24 hours) |
| `OPENROUTER_PROXY_URL` | Spring Boot | `http://localhost:8081` (local) or `http://openrouter-proxy:8080` (Docker) |
| `ENCRYPTION_MASTER_KEY` | Spring Boot | 64-char hex (32 bytes) for AES-GCM key encryption |

## Secret Generation

**JWT_SECRET** (Base64-encoded, ≥ 256 bits):
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))
```

**ENCRYPTION_MASTER_KEY** (64 hex chars = 32 bytes):
```bash
openssl rand -hex 32
```

## Rules

- Never commit `.env` — it is gitignored
- `ENCRYPTION_MASTER_KEY` must be exactly 64 hex characters (32 bytes) — AES-GCM key for encrypting stored user OpenRouter keys; generate with the command above
- `JWT_SECRET` must be Base64-encoded and ≥ 256 bits — JJWT enforces this at startup
- `OPENROUTER_API_KEY` is no longer injected into nginx for chat requests — nginx forwards the `Authorization` header from Spring Boot as-is (pass-through); the env var is kept for health check backwards compatibility

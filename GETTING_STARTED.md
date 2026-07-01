# Getting Started

This is a step-by-step guide to running AspectOR on your own machine, written for
someone who has just cloned the repo and has never seen the code before. If you
want the architecture overview, API reference, or DB schema, see [README.md](README.md)
and [docs/](docs/) instead — this guide only covers "how do I get it running."

There are two ways to run this project:

- **[Path A — Docker only](#path-a--docker-only-recommended)** — fastest, no Java or Node
  install needed. Good if you just want to use the app.
- **[Path B — Local development](#path-b--local-development)** — runs the backend and
  frontend outside Docker with hot-reload. Good if you want to change the code.

Both paths need Docker for the database and proxy either way.

---

## What you'll need

| Tool | Why | Get it |
|---|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Runs MySQL, the nginx proxy, and (Path A) everything else | Install and make sure it's **running** before you start |
| [Git](https://git-scm.com/downloads) | To clone the repo | — |
| A free [OpenRouter](https://openrouter.ai) account | The app proxies chat requests to OpenRouter's free models | Sign up, then create a key at [openrouter.ai/keys](https://openrouter.ai/keys) — no credit card needed for free-tier models |

Only needed for **Path B** (local dev):

| Tool | Version | Notes |
|---|---|---|
| Java | JDK 21 **and** JDK 25 | Gradle itself runs on 21; the app compiles/runs on 25 via Gradle Toolchains. See [memory/adrs/ADR-004](memory/adrs/ADR-004-java21-gradle-runtime-java25-toolchain.md). |
| Node.js | 22+ (npm 10+) | For the admin UI dev server |

---

## Path A — Docker only (recommended)

This starts all four services (nginx proxy, MySQL, Spring Boot, admin UI) in containers.
Nothing needs to be installed except Docker.

### 1. Clone the repo

```bash
git clone https://github.com/<your-fork-or-org>/secure-openrouter-docker.git
cd secure-openrouter-docker
```

### 2. Create your `.env` file

```bash
cp .env.example .env
```

Open `.env` in an editor and fill in each value. Here's what each one means and how
to generate it:

| Variable | What to put |
|---|---|
| `OPENROUTER_API_KEY` | Your key from [openrouter.ai/keys](https://openrouter.ai/keys), starts with `sk-or-v1-` |
| `MYSQL_ROOT_PASSWORD` | Any password — this is a local-only dev database |
| `MYSQL_DATABASE` | Leave as `openrouter_gateway` unless you have a reason to change it |
| `MYSQL_USER` / `MYSQL_PASSWORD` | Any username/password — used only by the app to connect to its own local DB |
| `JWT_SECRET` | A random Base64 string, **at least 32 bytes**. Generate one below. |
| `JWT_EXPIRATION_MS` | Leave as `86400000` (24 hours) unless you want sessions to expire sooner |
| `ENCRYPTION_MASTER_KEY` | A random 64-character hex string. Generate one below. |

Generate `JWT_SECRET`:

```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))
```
```bash
# macOS / Linux
openssl rand -base64 32
```

Generate `ENCRYPTION_MASTER_KEY`:

```bash
# All platforms (requires OpenSSL — Git Bash on Windows has it)
openssl rand -hex 32
```

Paste each generated value into the matching line in `.env`. **Never commit this file**
— it's already in `.gitignore`.

### 3. About the database — you don't need to install or download anything

You do not need to install MySQL, download a database file, or import a dump. The
`openrouter-mysql` service in `docker-compose.yml` points at the official
[`mysql:8.0`](https://hub.docker.com/_/mysql) image, and `docker compose up -d` (next
step) pulls it from Docker Hub automatically the first time you run it — that's where
"the db" comes from. It starts as a fresh, empty MySQL server; there is no seed
database file anywhere in this repo that you need to find or download.

The tables inside that empty server are then created automatically. On first startup,
Spring Boot's [Flyway](https://flywaydb.org/) integration automatically creates every table (users, chat logs, conversations, model
config, usage limits, preferences — 8 tables across `V1`–`V7`) and seeds the default
admin user and the initial free-model list. This happens every time you start the app
against a fresh database.

> ⚠️ **Ignore `db/seed.sql`.** It's a leftover from before Flyway was introduced, is
> explicitly marked deprecated in the file header, and is missing several tables added
> since (`user_model_preferences`, `model_usage_limits`, `user_model_usage`). It is not
> mounted into the MySQL container and running it manually will leave you with an
> incomplete/inconsistent schema. The real, complete, always-up-to-date schema is
> [`app/src/main/resources/db/migration/`](app/src/main/resources/db/migration/) — see
> [docs/schema.md](docs/schema.md) for a human-readable table-by-table breakdown.

### 4. Start everything

```bash
docker compose up -d
```

First run takes a few minutes — it's building the Spring Boot and admin UI images and
downloading the MySQL/Prometheus/Grafana images. Watch progress with:

```bash
docker compose ps
```

Wait until all services show `(healthy)`. MySQL is the slowest to become healthy
(~30 seconds) since Spring Boot waits for it before starting.

### 5. Open the app

Go to **http://localhost:3000** and log in with the seeded admin account:

```
Email:    admin@openrouter.local
Password: Admin@2026!
```

**Change this password immediately** (Settings → Change Password) — it's a well-known
default seeded by the DB migration, not a secret.

### 6. Try it out

- Go to **Model Manager** (admin) and enable a couple of free models — new installs sync
  the current OpenRouter free-tier list on first startup but leave everything disabled
  until you review it.
- Go to **Playground** and send a message to confirm the whole chain (browser → Spring
  Boot → nginx → OpenRouter) works end to end.
- Optional CLI smoke test instead of the browser:
  ```powershell
  .\test-request.ps1      # Windows
  ```
  ```bash
  ./test-request.sh       # macOS / Linux
  ```

That's it — you have a working local instance. Skip to [Verifying everything works](#verifying-everything-works)
if anything looks off, or [Stopping / resetting](#stopping--resetting) when you're done.

---

## Path B — Local development

Use this if you're going to edit the Java backend or React frontend and want fast
rebuild/reload cycles instead of rebuilding Docker images on every change.

### 1–2. Clone and set up `.env`

Same as Path A, [steps 1](#1-clone-the-repo) and [2](#2-create-your-env-file) above.

### 3. Start only the infrastructure in Docker

```bash
docker compose up -d openrouter-proxy openrouter-mysql
docker compose ps        # wait for both (healthy)
```

This leaves the Spring Boot app and admin UI running natively on your machine instead
of in containers, so you get instant rebuilds.

### 4. Run the backend

Make sure JDK 21 is first in your `PATH` (Gradle's own runtime requirement — the app
itself still compiles and runs on Java 25 via Toolchains, this only affects the Gradle
process). Check with:

```bash
java -version
```

If it's not 21, either reorder your `PATH` or set `JAVA_HOME` to your JDK 21 install
for this terminal session before continuing.

Then:

```cmd
run-app.bat
```

This loads `.env`, and runs `gradlew.bat bootRun`. The backend listens on
**http://localhost:8080**. Leave this terminal running.

On macOS/Linux there's no equivalent script yet — export the `.env` vars into your
shell yourself, then run `cd app && ./gradlew bootRun`.

### 5. Run the frontend

In a second terminal:

```bash
cd admin-ui
npm install     # first time only
npm run dev
```

Vite proxies `/api` requests to `localhost:8080` automatically. Open
**http://localhost:3000** and log in with the same default admin credentials as
Path A.

### Building without running

```cmd
cd app
gradlew.bat build -x test
```
```bash
cd admin-ui
npm run build
```

---

## Verifying everything works

| Check | Command / URL | Expect |
|---|---|---|
| All containers healthy | `docker compose ps` | Every service shows `(healthy)` |
| Backend health | `curl http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| nginx proxy health | `curl http://localhost:8081/health` | `200 OK` |
| Frontend loads | http://localhost:3000 | Login page renders |
| Login works | Log in with the default admin credentials | Redirects to dashboard |
| A model responds | Playground → send a message | Streamed reply appears |
| Metrics (optional) | http://localhost:9090 (Prometheus), http://localhost:3001 (Grafana, `admin`/`admin` unless overridden) | Dashboards load |

---

## Troubleshooting first run

| Symptom | Cause | Fix |
|---|---|---|
| `docker compose up -d` fails immediately | Docker Desktop isn't running | Start Docker Desktop, wait for it to say "running", retry |
| `openrouter-proxy` container restarts in a loop | `OPENROUTER_API_KEY` missing/blank in `.env` | Add a real key from [openrouter.ai/keys](https://openrouter.ai/keys) |
| `WeakKeyException` in Spring Boot logs | `JWT_SECRET` shorter than 32 bytes | Regenerate using the command above — it must be Base64 and ≥ 256 bits |
| `IllegalArgumentException: ENCRYPTION_MASTER_KEY must be 64 hex chars` | Key missing, wrong length, or not hex | Regenerate with `openssl rand -hex 32` |
| `openrouter-mysql` never turns healthy | First boot is slow, or a port conflict | Give it up to a minute; check nothing else is bound to `3309` |
| Port already in use (`3000`, `8080`, `8081`, `3309`) | Another process already using it | Stop the other process, or edit the port mapping in `docker-compose.yml` |
| Gradle fails with a "class file version" error | Gradle running on the wrong JDK | Put JDK 21 first in `PATH` for the Gradle process — the app itself still uses Java 25 |
| Login page loads but login fails | Fresh DB didn't seed the admin user | Check Flyway ran: `docker compose logs openrouter-app \| grep -i flyway`; the seed comes from a Flyway migration, not a script you run manually |
| Chat returns 409 in the UI | You're logged in as a non-admin user with no OpenRouter key saved | Go to Settings and add a personal OpenRouter API key (BYOK) |

If something isn't covered here, check [docs/constraints.md](docs/constraints.md) —
it has a much longer symptom → fix table organized by subsystem.

---

## Stopping / resetting

Stop everything, keep your data:
```bash
docker compose down
```

Stop everything and wipe the database (irreversible — deletes all users, chat history,
and conversations):
```bash
docker compose down -v
```

---

## Next steps

- [README.md](README.md) — architecture diagram, full API reference, security notes
- [docs/environment.md](docs/environment.md) — full explanation of every env var
- [docs/schema.md](docs/schema.md) — database tables and Flyway migration history
- [docs/constraints.md](docs/constraints.md) — gotchas and troubleshooting by subsystem
- [memory/adrs/](memory/adrs/) — why certain architectural decisions were made

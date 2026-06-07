# Learnings — Secure OpenRouter Gateway

Hard lessons from building this project. Each entry is a real problem we hit,
why it happened, and what to do differently next time.

---

## 1. nginx: `set` variable inside location block is unreliable

**What happened:** We tried injecting the API token by writing a `set $api_key "..."` snippet
into `/etc/nginx/conf.d/openrouter_token.conf` and including it inside the location block.
nginx started, health check passed, but all `/api/v1/` requests returned 404 with no error logs.

**Why:** The `set` directive inside a location block worked syntactically, but the variable
scoping caused nginx to silently fall through to `location / { return 404; }` at request time.

**Fix:** Use `envsubst` to substitute the token directly into the nginx config before nginx starts.
Store the template outside any `tmpfs` mount (`/nginx.conf.template`), write the final
`/etc/nginx/nginx.conf` in the entrypoint. Scope substitution: `envsubst '${OPENROUTER_API_KEY}'`
to avoid clobbering nginx's own `$remote_addr` etc.

**See:** ADR-002

---

## 2. `read_only: true` container + tmpfs mount ordering matters

**What happened:** We mounted `/etc/nginx` as tmpfs (to allow writing nginx.conf at runtime)
but stored the template at `/etc/nginx/nginx.conf.template`. The tmpfs wiped the template
before the entrypoint could read it.

**Why:** Docker mounts the tmpfs **after** the container filesystem is set up, which empties
the directory. Any files copied there by the Dockerfile are gone at startup.

**Fix:** Store the template at `/nginx.conf.template` (in root, outside any tmpfs mount).
Entrypoint reads from `/nginx.conf.template`, writes to `/etc/nginx/nginx.conf` (which is
on the writable tmpfs).

---

## 3. Gradle Kotlin DSL fails on Java 25

**What happened:** `build.gradle.kts` failed with `java.lang.IllegalArgumentException: 25.0.2`
inside the Kotlin compiler's `JavaVersion.parse()`.

**Why:** The Kotlin compiler bundled in Gradle 8.12–8.14 doesn't recognize Java 25's version string.

**Fix:** Switch to Groovy DSL (`build.gradle`). Identical build logic, no Kotlin compiler involved.

**See:** ADR-003

---

## 4. Gradle 8.14 does not support Java 25 as the runtime JVM

**What happened:** Running `gradlew.bat build` with Java 25 in PATH failed with
`Unsupported class file major version 69` (Java 25 = class file version 69).

**Why:** Gradle 8.14 only officially supports up to Java 24 as the Gradle daemon JVM.

**Fix:** Run Gradle on Java 21, use the Gradle toolchain feature to compile with Java 25.
`run-app.bat` handles this automatically with `switch-java-version.bat 21`.

**See:** ADR-004

---

## 5. shadcn@latest (radix-nova) generates oklch CSS variables, breaking Tailwind hsl() wrappers

**What happened:** After `npx shadcn@latest init`, all UI colors disappeared.
Dark mode appeared to not work. Mobile layout looked broken.

**Why:** shadcn's radix-nova style uses `oklch()` format for CSS variables.
Our `tailwind.config.ts` wrapped them as `hsl(var(--background))`.
The browser received `hsl(oklch(...))` — invalid CSS, renders as transparent.

**The trap:** This is easy to miss because the console shows no errors.
Everything renders but without any color.

**Fix:** Change all Tailwind color definitions from `hsl(var(--x))` to `var(--x)`.
`var()` passes through whatever format is in the CSS variable — works with both hsl and oklch.

**See:** ADR-006

---

## 6. shadcn's CommandDialog does not auto-wrap children in Command

**What happened:** `CommandInput` and `CommandList` rendered empty inside `CommandDialog`.
After wrapping in an explicit `Command`, keyboard navigation still didn't work.

**Why (two separate bugs):**
1. This `CommandDialog` is `Dialog > DialogContent` only — no `Command` wrapper.
   cmdk's `CommandInput` and `CommandList` need a `Command` ancestor to function.
2. A custom div overlay intercepted keyboard events before cmdk's handler.

**Fix:**
```tsx
<CommandDialog open={open} onOpenChange={setOpen}>
  <Command>                    {/* ← required, not automatic */}
    <CommandInput ... />
    <CommandList>...</CommandList>
  </Command>
</CommandDialog>
```

**See:** ADR-007

---

## 7. `outline-ring/50` Tailwind class breaks with var() color definitions

**What happened:** After fixing the oklch issue, the CSS threw:
`The 'outline-ring/50' class does not exist`

**Why:** Tailwind's opacity modifier (`/50`) requires the color token to support opacity.
Our `ring: 'var(--ring)'` definition doesn't expose an alpha channel for Tailwind to modify.

**Fix:** Remove `outline-ring/50` from `index.css`. The line was added by shadcn's init
inside a `@layer base { * { @apply border-border outline-ring/50; } }` block.

---

## 8. BCrypt hash in seed.sql was for "password", not "Admin@2026!"

**What happened:** The well-known test hash `$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.`
was used as a placeholder in `seed.sql`. This hash is for the password `password`, not `Admin@2026!`.

**Fix:** Generate real hashes using the running Spring Boot app's BCrypt encoder.
The safest approach: register via the API, then UPDATE the role to ADMIN via Navicat.
Never copy BCrypt hashes from the internet without knowing what password they encode.

---

## 9. Token estimation (4 chars = 1 token) is misleading, not just imprecise

**What happened:** We showed `~X / 32K tokens` based on character counting.
Users naturally trust displayed numbers. An inaccurate number is worse than no number.

**Why:** The 4:1 ratio is an average for English prose. Code, JSON, non-Latin scripts,
and special characters all tokenize very differently. The context window sizes were also guessed.

**Fix:** Show real token counts from OpenRouter's `usage` object in the API response.
Display "last response: X tokens (Y in / Z out)" instead of a live running estimate.

**See:** ADR-008

---

## 10. Responsive layout: don't rely on Tailwind breakpoint classes when CSS conflicts exist

**What happened:** `hidden md:flex` on the desktop sidebar didn't hide it on mobile.
The sidebar and content rendered side by side even at 390px width.

**Why:** CSS from `@import "shadcn/tailwind.css"` may override Tailwind utility classes
in unpredictable ways. `hidden md:flex` is a display property — any CSS with higher
specificity that sets `display` will win.

**Fix:** Use JavaScript-based responsive detection (`useIsMobile` hook with `window.innerWidth`)
instead of Tailwind breakpoint classes for critical layout visibility. 100% reliable regardless
of CSS conflicts.

---

## 11. seed.sql doesn't run automatically on existing MySQL volumes

**What happened:** `db/seed.sql` is mounted as a Docker init script
(`/docker-entrypoint-initdb.d/seed.sql`). MySQL only runs init scripts on the **first**
container start when the data directory is empty.

**Why:** Docker's MySQL image convention: init scripts run only on initialization.
If the volume already has data (from a previous run), init scripts are skipped.

**Fix for development:** Run the seed manually via Navicat or `docker compose exec`.
For production/fresh deploys: the Docker volume auto-runs seed on first start.
Document this clearly — developers with existing volumes will hit this.

---

## 12. Spring Boot ddl-auto=update adds new columns but doesn't backfill

**What happened:** Adding `active boolean` to `User.java` after the table existed.
The column was added by Hibernate on next startup, but existing rows got `NULL`
(despite the Java default of `true`), causing `active` checks to fail.

**Fix:** After adding a new column with a default via ddl-auto=update, run:
```sql
UPDATE users SET active = 1 WHERE active IS NULL;
```

For production, prefer explicit Flyway/Liquibase migrations over `ddl-auto=update`.
`update` is fine for local dev but risky in production.

---

## 13. OpenRouter 429 was silently treated as a success

**What happened:** When OpenRouter returned HTTP 429 (rate limit), the proxy forwarded it
as `ChatResult.Success(statusCode=429, body="{error:...}")`. `ConversationController`
treated any `Success` case as a valid response, saved an empty assistant message to the DB,
and returned 200 to the frontend. The UI showed an empty bubble with no error.

**Why:** `ChatService` returns `Success` for any completed proxy call — it only returns
`RateLimited` for our own Bucket4j rate limiter, not for upstream OpenRouter errors.

**Fix:** Check `s.statusCode() >= 400` before saving the message. Roll back the user message,
parse the OpenRouter error body, and return the appropriate HTTP status to the frontend.
Also restore the input text on the frontend so the user doesn't lose their message.

---

## 14. Conversation model doesn't update when switching models in the UI

**What happened:** User switched model via command palette → `setSelectedModel` updated
the frontend state but the active conversation still stored the old model in the DB.
Every subsequent message used the original model regardless of what the UI showed.

**Why:** `ConversationController.sendMessage` uses `conversation.getModel()` (from DB),
not the model the frontend currently has selected.

**Fix:** When switching to a different model, automatically start a new conversation
with the new model. The old conversation stays in history. This is also how Claude behaves —
model switches create a new conversation thread.

---

## 15. react-markdown needs explicit `components` prop to render code blocks

**What happened:** Installing `react-markdown` and wrapping message content in
`<ReactMarkdown>` rendered text correctly but code blocks still showed as plain text
with the triple-backtick fences visible.

**Why:** `react-markdown` renders code blocks as plain `<code>` elements by default.
Syntax highlighting requires overriding the `code` component with `react-syntax-highlighter`.

**Fix:** Pass a `components` prop with a custom `code` renderer that detects block vs inline
code via the `language-xxx` className, uses `Prism` for block highlighting,
and falls back to styled inline `<code>` for inline snippets.

---

## 16. radix-nova Tabs renders list and content side-by-side, not stacked

**What happened:** After adding Sign Up / Sign In tabs to the login page,
the `TabsList` rendered on the LEFT and `TabsContent` on the RIGHT — side by side,
not stacked vertically as expected.

**Why:** The radix-nova `Tabs` component uses `data-horizontal:flex-col` to set vertical
stacking when `orientation="horizontal"`. But `data-horizontal:` is a Tailwind attribute
variant matching `data-horizontal` (boolean attribute), not `data-orientation="horizontal"`.
The selector never matches, so the layout defaults to `flex` (row direction).

**Fix:** Add `className="flex-col"` directly to the `<Tabs>` component to force vertical stacking.

---

## 17. Tailwind oklch CSS variables break `oklch(from var(...) ...)` relative color syntax

**What happened:** Used CSS relative color syntax `oklch(from var(--primary) l c h / 25%)`
in inline styles to create tinted shadows. This worked in some browsers but not all,
and caused silent failures in the box-shadow on the user chat bubble.

**Why:** CSS relative color syntax (`oklch(from ...)`) is a newer CSS feature (baseline 2024).
Not all browser versions support it, especially in combination with CSS custom properties.

**Fix:** Use explicit oklch values for shadows rather than relative color syntax.
For primary-tinted shadows, hardcode the approximate oklch values or use opacity variants.

---

## 18. Hibernate 6 / MySQL8Dialect maps Java `boolean` to `BIT`, not `TINYINT`

**What happened:** Flyway V1 created boolean columns as `TINYINT` (common MySQL convention).
Spring Boot failed on startup with:
`SchemaManagementException: wrong column type [enabled] in table [model_config]; found [tinyint (Types#TINYINT)], but expecting [bit (Types#BOOLEAN)]`

**Why:** Hibernate 6 with `MySQL8Dialect` maps Java `boolean` → `Types#BOOLEAN` → `BIT`.
`ddl-auto=validate` enforces this strictly — it checks the JDBC `Types` constant, not just
the SQL type name. `TINYINT` and `BIT(1)` store the same byte but map to different JDBC types,
so validation fails even though data would be compatible.

**Fix:** Use `BIT(1)` in all Flyway CREATE TABLE statements for boolean columns.
Use `b'1'` / `b'0'` literals in seed INSERT statements for BIT columns (not `TRUE`/`FALSE`).

**Rule:** When using `ddl-auto=validate` with Flyway, derive column types from Hibernate's
generated DDL, not from general MySQL convention. The safest way to discover the exact
expected type is to read the `SchemaManagementException` error message.

---

## 19. Never edit a Flyway migration after it has been applied

**What happened:** V1 had `TINYINT(1)` (triggering a deprecation warning) and V2 had
`TRUE` instead of `b'1'` for BIT columns. Editing the files in place and restarting caused
Flyway to fail with a checksum mismatch — even in a dev environment where the DB had already
been wiped and recreated.

**Why:** Flyway checksums each migration file on every startup and compares against the
`flyway_schema_history` table. If the file changes after being applied, Flyway refuses to start.

**Fix for development:** Drop the database (`DROP DATABASE openrouter_gateway`), recreate it,
and restart the app — Flyway re-applies all migrations from scratch against a clean schema.

**Rule for production:** Never edit applied migrations. Add a new `V4__...sql` migration to
correct any issue. Editing V1–V3 is only acceptable in early development before the first
shared deployment, and requires all developers to reset their local DBs.

---

## 20. "No static resource" error means the controller was never registered

**What happened:** After writing a new `@RestController`, hitting the endpoint returned
`NoResourceFoundException: No static resource api/user/models` logged as an unhandled
exception in `GlobalExceptionHandler`. The class existed in the right package, annotations
looked correct, no obvious errors.

**Why:** Spring Boot was not restarted after the new files were written. The running JVM
had no knowledge of the new controller class — it never compiled or loaded it. Spring MVC
found no handler for the request, fell through to the static resource resolver, which also
found nothing, throwing `NoResourceFoundException`.

The `GlobalExceptionHandler` being present confirmed the app context had started — so the
controller's absence pointed to either: (a) app not restarted, or (b) bean creation failure
at startup that prevented the controller from registering.

**Diagnosis checklist:**
1. Was Spring Boot restarted after the new files were written? (`run-app.bat` in a fresh terminal)
2. Check startup logs for `BeanCreationException`, `FlywayException`, or `SchemaManagementException`
3. Verify the new Flyway migration applied: `SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC`
4. Check the controller's package is a sub-package of the `@SpringBootApplication` class

**Fix:** Stop `run-app.bat` (Ctrl+C), restart it. The new controller registers on the
next full application startup.

**Rule:** `gradlew.bat bootRun` (without DevTools hot-reload) requires a full restart to
pick up new classes. Never assume a running app has the latest code unless it was started
after the code was written.

---

## 21. Tomcat normalises `%2F` before Spring MVC — use integer PKs for slashed resource IDs

**What happened:** Model IDs (e.g., `meta-llama/llama-3.3-70b-instruct:free`) contain
forward slashes. URL-encoding them as `%2F` and using them as path variables appeared to
work in tests but failed in production because Tomcat normalises `%2F` → `/` before any
Spring filter or controller sees the request. Spring MVC then tried to match
`/api/user/models/meta-llama/llama-3.3-70b-instruct:free/toggle` against registered routes
and found nothing.

**Why:** This is a Tomcat connector behaviour, not a Spring MVC issue. The only workaround
(`ALLOW_ENCODED_SLASH=true`) is a known path-traversal attack vector that Spring Security
warns against. It cannot be safely enabled.

**Fix:** Use the `model_config` integer primary key as the path variable instead of the
string model ID. The frontend reads the integer `id` from the list response and uses it
for toggle/status calls. The string `modelId` is returned in response bodies for display
only — it never appears in a URL path segment.

**Rule:** Any resource identified by a string that could contain `/`, `:`, `?`, `#`, or
other URL-special characters must use a surrogate integer key in path variables. This is
not negotiable — URL encoding cannot reliably solve it at the Tomcat level.

**See:** ADR-015

---

## Session 2026-06-07: Subagent-driven development + worktree workflow

See [`2026-06-07-subagent-worktree-workflow.md`](2026-06-07-subagent-worktree-workflow.md)

Key lessons:
- Always run `git worktree list` before dispatching subagents — the worktree may already exist
- Subagent prompts must specify the worktree path as working directory, not the repo root
- Always set `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"` before running `gradlew.bat` from PowerShell

---

## Session 2026-06-08: Agent retry loop, SSE conversion, context

See [`2026-06-08-agent-retry-sse-and-context.md`](2026-06-08-agent-retry-sse-and-context.md)

Key lessons:
- Free-tier models vary widely in tool-use support; verify before setting as default
- A retry loop is invisible to the user without SSE — blocking + retry = perceived hang
- `EventSource` does not support POST; use `fetch` + `ReadableStream` for SSE over POST
- Gate on `gotDone` flag, not on response content truthiness
- Per-request context must be sent explicitly in every retry payload — no shared state between retries
- All outbound HTTP clients need explicit read + connect timeouts

# Constraints & Troubleshooting

Organized by subsystem. Each section lists rules first, then a symptom → fix table.

---

## 1. Authentication & JWT

### Rules

- **JWT secret must be Base64-encoded and ≥ 256 bits** — JJWT enforces this at startup; generate with the PowerShell command in `docs/environment.md`
- **`useAuth` must be a shared React Context** — calling `useAuth()` in multiple components creates independent `useState` instances; `AuthProvider` in `main.tsx` is the single source of truth; login updates one copy while `ProtectedRoute` reading another fresh null bounces back to `/login`
- **Axios 401 interceptor must guard auth endpoints** — the interceptor must check `!isAuthEndpoint && hadToken` before redirecting; wrong password on `/api/auth/login` returns 401 and must not trigger redirect
- **`AuthorizationDeniedException` must have a dedicated handler** — `AuthProvider` probes `/api/admin/stats` after every login; regular users always get 403; without a specific handler it falls through to the catch-all and logs at ERROR; handler returns 403 at DEBUG level with no stack trace

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `WeakKeyException` on startup | `JWT_SECRET` too short | Regenerate — see `docs/environment.md` |
| Login spins forever or redirects back | 401 interceptor firing on wrong-password response | Check `api.ts` interceptor guards `!isAuthEndpoint && hadToken` before redirect |
| Login always redirects to `/login` after success | `useAuth` not in shared context | Ensure `<AuthProvider>` wraps the app in `main.tsx`; all components must share one auth instance |
| `ERROR Unhandled exception: Access Denied` on every user login | `AuthorizationDeniedException` from `/api/admin/stats` falls through to catch-all | Add dedicated `@ExceptionHandler(AuthorizationDeniedException.class)` returning 403 at DEBUG level |
| Change password 401 | JWT not sent or user not found | Ensure Bearer token is in Authorization header; user must be active |

---

## 2. Database & Flyway

### Rules

- **Schema is owned by Flyway** — `ddl-auto=validate`; never change back to `update` or `create`
- **Never edit applied Flyway migrations** — V1–V7 are locked; add V8+ for any schema change; dev reset: drop DB and re-run
- **Boolean columns must be `BIT(1)` not `TINYINT`** — Hibernate 6 / MySQL8Dialect maps Java `boolean` → `BIT`; `TINYINT` causes `SchemaManagementException` on startup
- **`model_usage_limits` global row uses `user_id = NULL`** — always query with `IS NULL`, never `= NULL`
- **`@Transactional` required on controller methods accessing lazy collections** — Spring closes the Hibernate session after each repository call; add `@Transactional(readOnly = true)` to GET methods, `@Transactional` to write methods in `ConversationController`; missing this causes `LazyInitializationException`
- **`updated_at` is DB-managed** — `user_model_preferences.updated_at` uses `ON UPDATE CURRENT_TIMESTAMP`; entity field is `insertable=false, updatable=false` — never set in application code

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SchemaManagementException: wrong column type [enabled/active]` | Column created as `TINYINT` instead of `BIT(1)` | Drop DB and re-run with corrected migration using `BIT(1)` |
| Flyway checksum mismatch on startup | Applied migration file was edited | Never edit V1–V7; create V8+; dev reset: drop DB and re-run |
| `LazyInitializationException` on `Conversation.messages` | Controller method loads entity without `@Transactional` | Add `@Transactional(readOnly = true)` to `get()` and `@Transactional` to `sendMessage()` in `ConversationController` |
| Spring Boot fails with column error | New entity field, Hibernate not updated | Restart Spring Boot — `ddl-auto=validate` will surface schema drift |

---

## 3. SSE & Streaming

### Rules

- **SSE token data must be JSON-encoded** — raw `\n` in SSE `data:` fields is treated as an empty line by the protocol, silently dropping newlines; backend: `objectMapper.writeValueAsString(token)`; frontend: `JSON.parse(data)` before append
- **`SseEmitter.complete()` triggers ASYNC dispatcher** — Spring Security intercepts the async re-dispatch with no `SecurityContext` and throws `Access Denied`; fix: `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` in `SecurityConfig`
- **Send `event: error` before `completeWithError()`** — generic catch calling `completeWithError()` directly gives the client no feedback; always send the error SSE event first
- **Upstream 429 detection** — detect by `e.getMessage().contains("stream error 429")`; log at WARN not ERROR
- **Upstream 404 auto-disables the model** — `ModelConfigService.autoDisableRemovedModel()` is called when SSE catches `"stream error 404"`; sets `enabled = false` in DB, evicts cache, logs at WARN
- **429 from OpenRouter is not `ChatResult.RateLimited`** — it arrives as `Success(statusCode=429)`; check `statusCode` before saving
- **`normalizeMarkdown` must skip during streaming** — running normalization on partial content mis-detects separator rows; pass `isStreaming` prop and skip while `true`

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| SSE stream works but tables collapse to one line | `\n` tokens dropped by SSE protocol | Backend: `objectMapper.writeValueAsString(token)`; Frontend: `JSON.parse(data)` before append |
| `Access Denied` after SSE stream completes | Tomcat ASYNC dispatcher has no `SecurityContext` | Add `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` to `SecurityConfig` |
| Empty bubble after 429 | Upstream error treated as success | `ConversationController` checks `s.statusCode() >= 400` and rolls back |
| SSE stream closes silently on upstream 429 — no error shown | Generic catch calls `completeWithError()` without sending `event: error` first | Detect `"stream error 429"` in message, send error event to client, log at WARN |

---

## 4. Frontend & React

### Rules

- **`react-markdown` requires custom `code` component** — default rendering shows raw fences; override with `react-syntax-highlighter`
- **`remark-gfm` plugin required for GFM tables** — `react-markdown` does not parse pipe tables by default; add `remarkPlugins={[remarkGfm]}`; install: `npm install remark-gfm`
- **`Tabs` radix-nova renders side-by-side** — add `className="flex-col"` to force vertical stacking
- **Use `CommandDialog` not custom overlay** — cmdk keyboard nav requires proper focus trap
- **Wrap `CommandInput`/`CommandList` in `<Command>`** — `CommandDialog` does NOT auto-wrap children
- **Tailwind colors must use `var()` not `hsl(var())`** — shadcn radix-nova uses oklch CSS variables
- **`Write` tool pads files with null bytes** — strip with `tr -d '\0' < file > file.tmp && cp file.tmp file` after any Write/Edit operation that causes TypeScript "Invalid character" errors

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Code blocks render as plain text | Missing `react-markdown` `components` prop | Pass custom `code` renderer with `SyntaxHighlighter` to `<ReactMarkdown>` |
| Markdown tables render as raw text | Missing `remark-gfm` plugin | Add `remarkPlugins={[remarkGfm]}`; install: `npm install remark-gfm` |
| Tabs side by side instead of stacked | radix-nova `data-horizontal:flex-col` doesn't match | Add `className="flex-col"` to `<Tabs>` |
| ↑↓ not working in command palette | Missing `<Command>` wrapper in `CommandDialog` | Wrap `CommandInput`/`CommandList` in `<Command>` inside `CommandDialog` |
| Dark mode not working | CSS variable format mismatch | Ensure `tailwind.config.ts` uses `var()` not `hsl(var())` |
| TypeScript "Invalid character" on line N | Null byte padding from Write tool | Run `tr -d '\0' < file > file.tmp && cp file.tmp file` |
| `outline-ring/50` CSS error | shadcn radix-nova opacity modifier incompatible | Remove `outline-ring/50` from `index.css` |

---

## 5. Models & Sync

### Rules

- **Model switching starts a new conversation** — `conversation.model` is immutable after creation; switching model creates a fresh conversation
- **Integer PK in path, never string `modelId`** — model IDs contain `/` incompatible with Spring MVC path variables; all toggle/status paths use `model_config.id` (integer); the `modelId` string is in response bodies only
- **`toggleModel` uses atomic upsert** — `INSERT ... ON DUPLICATE KEY UPDATE enabled = NOT enabled`; load-or-create is forbidden (race condition: concurrent INSERTs violate unique constraint or produce wrong state via last-write-wins)
- **Admin gates, user filters** — users can never re-enable an admin-disabled model; `toggleModel` rejects with 400 (`ModelAdminDisabledException`)
- **ROLE_ADMIN bypasses user preferences** — `getEffectiveModels` short-circuits to full admin-enabled list when caller has `ROLE_ADMIN`; preference rows for admins are ignored
- **Absence of preference row = enabled** — `user_model_preferences` is sparse; do not pre-populate rows for every user/model combination
- **`totalUserEnabled` = effective visible count** — derived from admin-enabled ∩ user-enabled at query time; not the count of `enabled=true` DB rows
- **Empty-state condition lives in `useEffectiveModels` hook** — `totalUserEnabled === 0` check must not be duplicated across My Models page and Playground; both derive from the same hook
- **Optimistic UI must revert on failure** — toggle reverts to pre-click state on API error, accompanied by an error toast
- **`OWNER_GROUPS` must be in both files** — `ModelManagerPage.tsx` and `MyModelsTab.tsx` both group by owner (NVIDIA, Meta, Google, etc.); update both files when providers change
- **`FreeModelSyncService` deduplicates** — if OpenRouter returns both `X` (pricing=0) and `X:free`, only `X:free` is inserted; prevents duplicate display names
- **New models default to `enabled=false`** — `FreeModelSyncService` never auto-enables; admin reviews and enables in Model Manager after review
- **`AppStartupRunner` is non-fatal** — sync failure at startup logs WARN and lets the app start; retry via `POST /api/admin/sync-models`

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Model switch not working | Conversation stores model at creation | Switching model creates a new conversation — this is by design |
| `PUT /api/user/models/{id}/toggle` returns 400 | Model is admin-disabled | Admin must enable first; user cannot override admin gate |
| `PUT /api/user/models/{id}/toggle` returns 404 | Wrong `id` — must be `model_config` integer PK, not string `modelId` | Use `UserModelDto.id` (number) from `GET /api/user/models` |
| Playground dropdown empty after toggle | `useEffectiveModels` not refreshed | Toggle refreshes hook state automatically; if stale, call `refresh()` from hook |
| My Models tab shows no models | `GET /api/user/models` failed or returned empty | Check Spring Boot logs; verify V5 migration: `SELECT * FROM flyway_schema_history` |
| My Models tab shows flat list, Model Manager shows grouped | `OWNER_GROUPS` only in `ModelManagerPage` | Update `OWNER_GROUPS` in both `ModelManagerPage.tsx` and `MyModelsTab.tsx` |
| Duplicate models in My Models / Model Manager | Sync inserted both `X` and `X:free` | V7 migration cleans up existing pairs; `FreeModelSyncService` dedup prevents recurrence |
| `POST /api/admin/sync-models` returns 500 | OpenRouter `/api/v1/models` unreachable | Check network; sync is non-fatal on startup but returns 500 on manual trigger |
| Startup sync added models that shouldn't be free | OpenRouter model has `pricing={"prompt":"0"}` temporarily | Admin can disable in Model Manager; sync never auto-enables |
| Sync button shows "Syncing…" indefinitely | Request timed out (30s) or Spring Boot unreachable | Check Spring Boot logs; timeout is 30s for the OpenRouter API call |
| `No endpoints found` from OpenRouter | Model removed from free tier | Run `test-models.ps1`; model auto-disabled on next 404 SSE error |

---

## 6. Agent (PRD-005)

### Rules

- **BYOK key required** — `POST /api/agent/chat` is admin-only and requires a configured BYOK key; returns 409 (`KeyNotConfiguredException`) if none set
- **`OpenRouterAdapter` is the only class that knows OpenAI format** — `AgentService` speaks Claude API format (`stop_reason`, `tool_use` blocks, `tool_result` blocks); swapping to Anthropic SDK = replace adapter only
- **MAX_TURNS = 10** — hard guard in `AgentService` to prevent runaway tool-call loops
- **Agent endpoint is SSE** — `POST /api/agent/chat` produces `text/event-stream`; use `fetch` + `ReadableStream`; `EventSource` does not support POST
- **Retry loop** — `AgentService.run()` builds a candidate list (requested model first, then all DB-enabled models); on `ModelRateLimitedException` or `ModelToolUseNotSupportedException` it tries the next candidate; exhausted list → `AllModelsUnavailableException` (503)
- **`ResourceAccessException` treated as rate-limited** — 25s read / 5s connect timeout via `SimpleClientHttpRequestFactory`; timeout throws `ResourceAccessException` which is caught and re-thrown as `ModelRateLimitedException` so the retry loop continues
- **`gotDone` flag, not `finalReply` truthiness** — some models return empty text on the final turn (tool-only turn); frontend must check `gotDone` alone when deciding to render the agent reply bubble; never gate on `finalReply` being truthy
- **`history` role mapping** — frontend must map agent messages to `role: 'assistant'` before sending; never send `role: 'agent'`
- **`ModelRateLimitedException` is retry-internal** — never mapped to an HTTP status; caught only inside `AgentService.run()`
- **Default model is `meta-llama/llama-3.3-70b-instruct:free`** — `nvidia/nemotron-nano-9b-v2:free` does not reliably support function calling

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Agent sends message but nothing appears in chat | Model returned empty text (tool-only final turn); `gotDone && finalReply` guard drops it | Check `gotDone` alone; `finalReply` being empty is valid — render fallback text |
| Agent 503 "all models unavailable" | Every enabled model rate-limited or no tool support | Enable more models in Model Manager; wait for upstream rate limits; 25s timeout per model |
| Agent loses conversation context after model switch | `history` not sent, or `role: 'agent'` not converted to `'assistant'` | Map agent messages to `role: 'assistant'` before sending; history is per-request |
| Agent status SSE events not received | Using `EventSource` which doesn't support POST | Switch to `fetch` + `ReadableStream`; parse `event:`/`data:` lines manually |
| Agent 400 "does not support tool use" | Model returned 404 from OpenRouter — no tool support | Retry loop auto-skips; enable only tool-capable models in Model Manager |
| Agent hangs past 25s with no progress event | `ResourceAccessException` not surfacing — adapter timeout not applied | Verify `SimpleClientHttpRequestFactory` is set on `RestClient` in `OpenRouterAdapter` |

---

## 7. Security & Infrastructure

### Rules

- **Never expose ports on `0.0.0.0`** — all ports bind to `127.0.0.1` only
- **Never hardcode the OpenRouter API key** — envsubst only; never in image layers
- **Never expose stored OpenRouter keys via HTTP** — `openrouterKeyEncrypted` is decrypted in-process only; no endpoint returns the plaintext value
- **nginx Authorization is pass-through** — Spring Boot sets `Authorization: Bearer {userKey}`; nginx forwards it with `$http_authorization`
- **`ENCRYPTION_MASTER_KEY` must be 64 hex chars (32 bytes)** — see `docs/environment.md` for generation
- **Gradle wrapper only** — never run `gradle` directly; always use `gradlew.bat`
- **Gradle runtime must be Java 21** — Gradle 8.14 does not support Java 25; Toolchain auto-provisions JDK 25 for compilation; Gradle itself requires JDK 21 in PATH
- **`KeyNotConfiguredException` → 409** — user tried to chat without a saved API key; frontend must redirect to Settings
- **`UsageLimitExceededException` → 429 with `resetAt`** — includes `limitType` ("request"/"tokens") and ISO `resetAt`
- **Request limit is pre-call, token limit is post-call** — request count checked before forwarding (unknown tokens); token count checked post-call; if over limit, next call is hard-blocked

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| nginx container restarting | `OPENROUTER_API_KEY` missing | Add key to `.env` |
| Admin login fails | User not in DB or wrong hash | Register via API, update role via Navicat |
| Chat returns 409 | User has no API key configured | User must go to Settings → add OpenRouter API key |
| `IllegalArgumentException: ENCRYPTION_MASTER_KEY must be 64 hex chars` | Key missing or wrong format | Generate: `openssl rand -hex 32`, set in `.env` |
| Gradle requires JDK 21 in PATH | `gradlew.bat` fails with wrong Java version | Ensure `JAVA_HOME` or PATH points to JDK 21 — Toolchain handles JDK 25 compilation automatically |
| `KeyNotConfiguredException` in logs | `getKeyForUser` called for user with no key | Expected — 409 returned to client; no action needed |
| Usage not incrementing | Token count 0 from OpenRouter | OpenRouter sometimes omits `usage` in stream; counters stay at 0 for that request |

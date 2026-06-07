# Learnings — 2026-06-08 Session (Agent Retry Loop, SSE Conversion, Context)

## 1. Free-tier models vary widely in tool-use support

**What happened:** The initial default model (`nvidia/nemotron-nano-9b-v2:free`) returned HTTP 404
from OpenRouter when a request included a `tools` parameter. The agent returned 500 with no useful message.

**Root cause:** Not all free-tier models support the OpenAI function-calling (`tools`) parameter.
OpenRouter returns 404 for unsupported requests, not 422 or 400.

**Fix:**
- Default changed to `meta-llama/llama-3.3-70b-instruct:free` — verified tool-capable.
- `OpenRouterAdapter` maps `HttpClientErrorException.NotFound` → `ModelToolUseNotSupportedException`
  so the retry loop skips the model instead of propagating a 500.

**Rule:** When adding a new free model as a default, verify it supports `tools` before shipping.
OpenRouter's model list API returns a `supported_parameters` field — check it.

---

## 2. A retry loop needs SSE to be useful — blocking is invisible to the user

**What happened:** The backend retry loop worked (WARN logs showed it trying models), but the
frontend showed a static spinner the entire time. Users had no idea if it was working or hung.

**Root cause:** `POST /api/agent/chat` returned blocking JSON. There was no channel to send
per-model progress events without restructuring as SSE.

**Fix:** Converted endpoint to `text/event-stream`. Backend emits:
- `event: status` before each attempt (`{type:"trying", model, attempt, total}`)
- `event: status` after each failure (`{type:"skipped", model, reason}`)
- `event: done` on success; `event: error` on total failure

Frontend uses `fetch` + `ReadableStream` (not `EventSource` — POST is unsupported by EventSource).
Status bubble updates live with model names and attempt counts.

**Rule:** Any operation that retries or fans out to multiple backends should be SSE from the start.
A blocking endpoint that retries internally is indistinguishable from a hang to the user.

---

## 3. EventSource does not support POST — use fetch + ReadableStream for SSE over POST

**What happened:** Standard browser `EventSource` API only supports GET. The agent endpoint
requires POST (body carries question, model, history).

**Fix:** Use `fetch` with `response.body.getReader()` and a manual SSE line parser:
- Split incoming chunks on `\n`
- Keep the last incomplete line in a buffer
- Dispatch events when an empty line is encountered (`event:` + `data:` accumulated)

**Rule:** For any SSE endpoint that requires a POST body, always use `fetch` + `ReadableStream`.
Do not attempt to work around this with EventSource query parameters for sensitive data.

---

## 4. Gate on `gotDone` flag, not on `finalReply` truthiness

**What happened:** When a model's final turn was tool-only (no accompanying text), `reply` was `''`.
Frontend checked `if (gotDone && finalReply)` — empty string is falsy, so no message was added.
The user saw loading stop with no result — a silent failure.

**Root cause:** Logical conflation of "did the response complete?" and "did it have text?".
These are independent. A valid agent response can have tool steps but no final text.

**Fix:** `if (gotDone)` only. Empty reply renders a fallback: `'(No text response — see tool calls above)'`.

**Rule:** Never use the content of a response to determine whether the response arrived.
Use a dedicated flag (`gotDone`, `received`, `complete`) for that purpose.

---

## 5. Per-request context must be sent explicitly — retry loops have no shared state

**What happened:** The agent retry loop switched to a different model. That model had no knowledge
of the previous conversation visible in the UI — each request started fresh with only the current question.

**Root cause:** `AgentRequest` only had `question` + `model`. No history was passed. Each
`runWithModel` call built its message list from scratch.

**Fix:**
- `AgentRequest` gained `List<HistoryMessage> history` (nullable, optional)
- Frontend maps UI messages to `{role, content}` pairs (agent → assistant) and sends them on every request
- `AgentService.runWithModel` prepends history into the `ClaudeMessage` list before the current question

**Rule:** When a loop retries with different backends (models, providers, regions), the retry
context must be embedded in the per-call payload — not stored in a shared service. Shared state
between retries is a concurrency footgun; repeatable payloads are safer and stateless.

---

## 6. ResourceAccessException (timeout) should be treated as transient, not fatal

**What happened:** A model that was slow or stalled would block the virtual thread indefinitely.
The `RestClient` had no configured timeout.

**Fix:**
- `SimpleClientHttpRequestFactory` set on the `RestClient`: 25 s read, 5 s connect
- `ResourceAccessException` (Spring's timeout wrapper) caught in `OpenRouterAdapter` and re-thrown
  as `ModelRateLimitedException` so the retry loop treats it as "skip to next model"

**Rule:** All outbound HTTP clients should have explicit read and connect timeouts. The default
(no timeout) means one slow upstream can block a thread for minutes.

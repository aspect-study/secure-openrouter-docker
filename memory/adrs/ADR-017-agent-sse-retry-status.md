# ADR-017 — Agent Endpoint Uses SSE for Real-Time Retry Status

**Date:** 2026-06-08  
**Status:** Accepted

## Context

`POST /api/agent/chat` was initially implemented as a blocking JSON endpoint.
The backend `AgentService` auto-retries across all DB-enabled models when the
primary model is rate-limited (429) or doesn't support tool use (404).

During a retry scenario the backend logs WARN messages per skipped model, but the
frontend had no way to surface this — it showed a static spinner for the entire
duration, which users could not distinguish from a hang.

## Decision

Convert `POST /api/agent/chat` to `text/event-stream`. The controller emits SSE
events throughout the retry loop, then a final `done` or `error` event.

**Event protocol:**
```
event: status   data: {"type":"trying","model":"...","attempt":N,"total":N}
event: status   data: {"type":"skipped","model":"...","reason":"rate_limited"|"tool_unsupported"}
event: done     data: {"reply":"...","toolSteps":[...],"modelUsed":"..."}
event: error    data: {"error":"...","status":409|400|503|500}
```

Frontend uses `fetch` + `ReadableStream` (not `EventSource`, which doesn't support POST).

## Rationale

- The existing pattern (`ConversationController.streamMessage`) already uses `SseEmitter` +
  `Thread.ofVirtual()`. Applying the same pattern to the agent endpoint is low-risk.
- Real per-model status ("Trying llama-3.3…", "rate-limited, trying next…") is far more
  useful than generic cycling messages or no feedback at all.
- `AgentService.run()` already accepted a `Consumer<Map<String, Object>>` for status events;
  the controller just wires it to `emitter.send()`.
- A blocking endpoint that retries internally is behaviorally identical to a hang from the
  user's perspective. SSE is the correct tool whenever progress is unknowable upfront.

## Consequences

- Frontend must use `fetch` + `ReadableStream` — `EventSource` doesn't support POST.
- The same `DispatcherType.ASYNC` security rule (already present from conversation streaming)
  covers this endpoint; no SecurityConfig change needed.
- `GlobalExceptionHandler` handlers for `AllModelsUnavailableException` and
  `ModelToolUseNotSupportedException` are not reached for agent requests — exceptions are
  caught inside the virtual thread and sent as `event: error` SSE events. The handlers
  remain for potential future non-SSE callers.
- `AgentResponse.modelUsed` is now part of the `done` payload so the frontend can update
  the model selector when a fallback model was used.

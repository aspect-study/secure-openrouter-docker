# ADR-012 — JSON-Encode SSE Token Data

**Date:** 2026-05-31  
**Status:** Accepted

## Context

The SSE protocol defines `data:` fields as text terminated by a newline. When a model outputs a `\n` character as a delta token, sending it as a raw `data: \n` line causes the SSE parser to interpret the newline as an empty-line event separator, silently dropping the newline. This collapsed multi-line responses (tables, code blocks, paragraphs) into a single line on the frontend.

## Decision

JSON-encode every token on the backend before sending as SSE data:

```java
emitter.send(SseEmitter.event().name("token").data(objectMapper.writeValueAsString(token)));
```

Decode on the frontend before appending:

```ts
let token = data
try { token = JSON.parse(data) } catch { /* fallback */ }
```

## Rationale

- JSON encoding turns `\n` into `"\n"` (two characters), which survives SSE transport intact.
- Zero new dependencies — `ObjectMapper` already exists.
- Backward-compatible fallback: if JSON parse fails, raw data is used.

## Consequences

- Frontend SSE parser must use `.replace(/^ /, '')` (strip one optional leading space per spec) instead of `.trim()` on the `data:` field value — trimming would strip meaningful whitespace from the JSON string.
- Frontend must check `currentEvent` (not `currentData`) when deciding to fire an event handler — empty string is a valid JSON-decoded value (`""`).

# ADR-011 — SSE Streaming with SseEmitter + Virtual Threads (no WebFlux)

**Date:** 2026-05-31  
**Status:** Accepted

## Context

The AI Playground needed streaming responses so users see tokens as they arrive rather than waiting for the full response. Two options were considered: Spring WebFlux (reactive) or Spring MVC with `SseEmitter` and virtual threads.

## Decision

Use `SseEmitter` (Spring MVC) with `Thread.ofVirtual().start()` — no WebFlux.

## Rationale

- The entire stack (SecurityConfig, JPA, Bucket4j, ChatLog) is built on blocking Spring MVC. Introducing WebFlux would require migrating all of this to reactive equivalents.
- Virtual threads (Java 21+) give concurrency without reactive complexity. A blocking `HttpClient.ofLines()` call on a virtual thread is effectively non-blocking for the platform.
- `SseEmitter` with a 120-second timeout handles the full streaming lifecycle cleanly.

## Consequences

- Rate limit check is done synchronously **before** spawning the virtual thread so a 429 can return a proper `ResponseEntity` instead of an already-open SSE connection.
- `SecurityConfig` must permit `DispatcherType.ASYNC` — Tomcat re-dispatches the response through the security filter chain on `emitter.complete()` with no SecurityContext, causing `Access Denied` without this rule.
- `AsyncRequestNotUsableException` must be handled silently in `GlobalExceptionHandler` — client disconnects mid-stream throw this on the Tomcat thread.
- `SseEmitter` errors (client disconnect) are caught inside the virtual thread lambda and forwarded via `emitter.completeWithError()`.

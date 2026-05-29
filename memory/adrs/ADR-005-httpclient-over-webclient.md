# ADR-005: Use Java HttpClient instead of WebClient or RestTemplate

**Date:** 2026-05-29
**Status:** Accepted

## Context

Spring Boot offers several HTTP client options for calling external APIs:
- `RestTemplate` — synchronous, familiar, being soft-deprecated
- `WebClient` — reactive, non-blocking, from Spring WebFlux
- `HttpClient` — JDK built-in (Java 11+), synchronous

## Decision

Use `java.net.http.HttpClient` (JDK built-in).

## Reasons

- **Virtual threads make blocking irrelevant:** With `spring.threads.virtual.enabled=true`, every Tomcat request thread is a virtual thread. Blocking I/O on a virtual thread is cheap — it unmounts from the carrier thread and doesn't block OS threads. WebClient's reactive model provides no meaningful advantage here.
- **No extra dependencies:** HttpClient is in the JDK. WebClient requires `spring-boot-starter-webflux` + Project Reactor.
- **Simplicity:** Blocking code is easier to read, debug, and reason about than reactive chains for a gateway that does one thing — forward a request and return the response.
- **RestTemplate avoided:** It's not officially deprecated but new Spring guidance recommends against it for new projects.

## Trade-offs

- WebClient would be slightly more efficient at very high concurrency without virtual threads — not relevant for this use case.
- If the project ever moves to full reactive (WebFlux), this would need to change.

## When to revisit

If the project adopts Spring WebFlux throughout, switch to WebClient for consistency.
If streaming SSE responses become a requirement, WebClient handles them more naturally.

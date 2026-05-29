# ADR-001: Use nginx as reverse proxy instead of calling OpenRouter directly

**Date:** 2026-05-29
**Status:** Accepted

## Context

The Spring Boot backend needs to call OpenRouter's API. Two options were considered:
1. Call `openrouter.ai` directly from Spring Boot, holding the API key in application config
2. Route through an nginx reverse proxy that injects the token

## Decision

Use nginx as a reverse proxy between Spring Boot and OpenRouter.

## Reasons

- **Token isolation:** The API key never appears in the Spring Boot application or its config. It lives only in `.env` and is injected into nginx at container startup via `envsubst`. This means even if the JAR is decompiled or logs are leaked, the token is not exposed.
- **Separation of concerns:** nginx handles transport security (TLS verification to openrouter.ai), token injection, and connection management. Spring Boot handles business logic.
- **Foundation for Phase 3+:** When multiple services need to call OpenRouter (admin UI, background workers), they all go through the same proxy. Token rotation requires changing one place.
- **Architecture learning goal:** The project is also a learning exercise in Docker + nginx proxy patterns.

## Trade-offs

- Added complexity for a solo project — calling OpenRouter directly would be simpler
- Extra network hop (localhost) adds ~1ms latency
- nginx must be running for Spring Boot to function

## Alternatives Rejected

- **Direct HttpClient call from Spring Boot:** Simpler, but token would live in `.env` and be read by the Java app — not ideal for a security-focused prototype.

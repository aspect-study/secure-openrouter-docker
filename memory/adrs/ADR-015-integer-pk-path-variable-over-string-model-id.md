# ADR-015: Integer PK in Path Variables Instead of String Model ID

**Date:** 2026-06-01  
**Status:** Accepted  
**Feature:** PRD-003 — User-Level Model Preferences

---

## Context

The `model_config` table uses a string model ID as the OpenRouter identifier
(e.g., `meta-llama/llama-3.3-70b-instruct:free`). When designing the toggle and
status endpoints for PRD-003, the first instinct was to use this string in the URL path:

```
PUT /api/user/models/meta-llama%2Fllama-3.3-70b-instruct%3Afree/toggle
```

This failed for a fundamental reason: Tomcat normalises `%2F` (encoded forward slash)
back to `/` **before** Spring MVC sees the request. The only workaround is enabling
`ALLOW_ENCODED_SLASH=true` in the Tomcat connector, which Spring Security explicitly
warns against (known path-traversal risk — CVE class).

---

## Decision

Use the `model_config` integer primary key (`id`) as the path variable for all
user model preference endpoints:

```
GET    /api/user/models/{id}/status   — {id} = model_config.id (Long)
PUT    /api/user/models/{id}/toggle   — {id} = model_config.id (Long)
```

The `modelId` string (e.g., `meta-llama/llama-3.3-70b-instruct:free`) is returned
in response bodies only and is never used in a URL path segment.

The frontend reads `UserModelDto.id` (integer) from `GET /api/user/models` and
uses that value for all subsequent toggle/status calls.

---

## Alternatives Considered

**`ALLOW_ENCODED_SLASH=true`** — Rejected. Spring Security documents this as a
path-traversal attack vector. Enables directory traversal attacks via `%2F` in
path segments.

**Custom PathVariable converter** — Rejected. The decoding happens at the Tomcat
connector level, before any Spring filter or converter runs. Application-level
decoding cannot recover what Tomcat has already normalised.

**Query parameter instead of path variable** — e.g., `PUT /api/user/models/toggle?modelId=...`
This would work technically but is non-RESTful and requires input sanitisation.
Rejected in favour of the cleaner integer PK approach.

**`@MatrixVariable`** — Rejected. Requires enabling matrix variable support in
Spring MVC configuration and is non-standard.

---

## Consequences

- Path variables are always safe integers — no encoding concerns
- Controller binding is simple: `@PathVariable Long id`
- `model_config.id` is stable (auto-increment PK, never changes once inserted)
- Frontend must use `UserModelDto.id` (number field) for API calls — `modelId`
  string is display-only. This must be documented and enforced in code review.
- The same pattern applies to any future endpoint whose resource has a slashed
  string identifier (e.g., if other API resources were ever keyed by model strings)

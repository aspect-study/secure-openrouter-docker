# ADR-010: Flyway for Schema Management over ddl-auto=update

**Date:** 2026-05-31  
**Status:** Accepted

---

## Context

The project used `spring.jpa.hibernate.ddl-auto=update` since Phase 2. This worked fine
for local development but has known production risks:

- `update` never removes columns or indexes — schema drift accumulates silently
- If a new `NOT NULL` column is added, existing rows get `NULL` (not the Java default)
  and the app silently fails until manually corrected (this already happened — see Learning 12)
- No audit trail of when schema changed or why
- `db/seed.sql` mounted as a Docker init script only ran on first container start — any
  developer with an existing MySQL volume would silently miss seed data (Learning 11)
- No way to reliably reproduce the exact schema on a fresh environment

---

## Decision

Replace `ddl-auto=update` with **Flyway** for schema management:

- `ddl-auto=validate` — Hibernate checks table/column existence only; Flyway owns all DDL
- Migrations live in `app/src/main/resources/db/migration/` (on the classpath, inside the JAR)
- `db/seed.sql` removed from the Docker volume mount; deprecated as reference-only
- Admin user and model_config seed data moved into Flyway V2/V3 migrations

---

## Alternatives Considered

**Liquibase** — more feature-rich (rollback support, XML/YAML/JSON formats), but heavier
and less ergonomic for a single-developer project. Flyway's SQL-native format is easier
to read and review.

**Keep ddl-auto=update** — acceptable for local dev but not for Phase 5 CI/CD. A CI pipeline
deploying to a shared environment needs reproducible, versioned schema changes.

---

## Implementation Notes

**Critical type mapping:** Hibernate 6 / `MySQL8Dialect` maps Java `boolean` → `Types#BOOLEAN` → `BIT`.  
`ddl-auto=validate` enforces this at the JDBC type level. Flyway migrations **must** use `BIT(1)`
for boolean columns — `TINYINT(1)` or `TINYINT` will cause `SchemaManagementException` on startup.

**Seed INSERT literals:** Use `b'1'` / `b'0'` for BIT column values in seed SQL,
not `TRUE`/`FALSE` (which are integer aliases and may cause implicit type coercion warnings).

**model_config.created_at default:** The column has `DEFAULT CURRENT_TIMESTAMP(6)` so
Flyway seed inserts can omit it. Without this default, MySQL strict mode raises a warning
when a `NOT NULL` column has no value in a raw INSERT.

**Immutability rule:** Never edit V1/V2/V3 after they have been applied to any database.
For schema corrections, add V4+. For dev-only resets: `DROP DATABASE openrouter_gateway` + restart.

**baseline-on-migrate=false:** Correct for fresh volumes. Set to `true` only when introducing
Flyway to an existing database that already has tables (production migration scenario).

---

## Consequences

- Schema is version-controlled, auditable, and reproducible
- Fresh environment setup is fully automated (no manual Navicat steps)
- `ddl-auto=validate` catches schema drift immediately on startup rather than silently
- Any schema change requires a new migration file — slightly more friction in development,
  but eliminates the class of bugs caused by silent Hibernate DDL mutations

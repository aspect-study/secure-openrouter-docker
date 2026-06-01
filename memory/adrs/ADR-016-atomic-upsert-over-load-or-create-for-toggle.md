# ADR-016: Atomic Upsert for Toggle Instead of Load-or-Create

**Date:** 2026-06-01  
**Status:** Accepted  
**Feature:** PRD-003 — User-Level Model Preferences

---

## Context

User model preference toggles need to flip a boolean state. The naive implementation is
a load-or-create pattern:

```java
// WRONG — race condition
Optional<UserModelPreference> existing = repo.findByUserIdAndModelId(userId, modelId);
if (existing.isPresent()) {
    existing.get().setEnabled(!existing.get().isEnabled());
    repo.save(existing.get());
} else {
    repo.save(new UserModelPreference(user, modelId, false)); // first toggle = disable
}
```

Under concurrent requests (e.g., user double-clicks the toggle), two threads both
see `Optional.empty()` and both attempt `INSERT`. The second INSERT violates the
`UNIQUE KEY uk_user_model_pref (user_id, model_id)` constraint → `DataIntegrityViolationException` (500).

Or: two threads both read an existing row with `enabled = true`, both flip to `false`,
last-write-wins → always `false` regardless of how many times toggled.

---

## Decision

Use a native MySQL `INSERT ... ON DUPLICATE KEY UPDATE` as an atomic upsert:

```sql
INSERT INTO user_model_preferences (user_id, model_id, enabled)
VALUES (:userId, :modelId, b'0')
ON DUPLICATE KEY UPDATE enabled = NOT enabled
```

Behaviour:
- **No row exists:** inserts with `enabled = b'0'` (first explicit toggle = disable;
  consistent with sparse-row semantics where absence = enabled).
- **Row exists:** flips `enabled` atomically via `NOT enabled`.

This is a single DB round-trip that is safe under any level of concurrency without
application-level locking.

---

## Sparse-Row Semantics

`user_model_preferences` is a sparse table — a row is only written when a user
explicitly toggles a model. Absence of a row = model is enabled (the default).

The first INSERT inserts `enabled = b'0'` because the row not existing means the
user currently sees the model (enabled by default). Their first toggle should hide it.
Subsequent toggles flip the existing row correctly.

---

## Alternatives Considered

**Pessimistic lock (`SELECT ... FOR UPDATE`)** — Prevents concurrent reads but
introduces lock contention and requires a transaction spanning the read + write.
More complex, slower, still requires two round-trips.

**Optimistic locking (`@Version`)** — Prevents lost updates via retry on version
mismatch. Requires application retry logic and still does two round-trips. More
complex for a simple boolean flip.

**`REPLACE INTO`** — Deletes the old row and inserts a new one. Loses `created_at`
and triggers cascading deletes. Not appropriate here.

---

## Implementation Notes

- The `upsertToggle` method in `UserModelPreferenceRepository` is annotated
  `@Modifying @Transactional @Query(..., nativeQuery = true)`.
- After `upsertToggle`, the service re-fetches the row to read the post-toggle state
  for the response DTO. This re-fetch assumes single-node / read-from-primary.
  If read replicas are introduced, ensure the re-fetch routes to primary.
- `save()` is never called in the toggle path. Any future code review seeing
  `repo.save(pref)` in a toggle flow should treat it as a bug.

---

## Consequences

- Toggle is race-safe at the DB level with no application-level locking
- Single round-trip for the state mutation (plus one re-fetch for the response)
- The pattern is non-obvious — the repository Javadoc documents the race condition
  explicitly so future developers understand why load-or-create is forbidden here

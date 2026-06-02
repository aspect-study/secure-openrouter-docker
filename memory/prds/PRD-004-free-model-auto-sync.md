# PRD-004 — Auto-Sync New Free Models from OpenRouter

**Status:** DONE — shipped 2026-06-02  
**Created:** 2026-06-01  
**Related:** Phase 4.9, V7 migration (duplicate cleanup)

---

## Problem

When OpenRouter adds a new free model, it never appears in AspectOR's Playground
or Model Manager unless a developer manually:
1. Discovers the new model (by checking openrouter.ai/models?max_price=0)
2. Adds it to `V2__seed_model_config.sql` (historical only — can't re-run)
3. Inserts it via a new Flyway migration or direct SQL

This is brittle and slow. The system already handles removals reactively (404 auto-disable),
so additions should be equally automatic.

---

## Goals

- Discover new free models added to OpenRouter without any manual steps
- Give admins visibility and control — new models default to **disabled** pending review
- Never auto-enable unknown models (quality/stability unknown)
- Startup sync ensures the DB is current on every deploy

---

## Non-Goals

- Scheduled/cron sync (startup + manual is sufficient)
- Auto-enabling new models
- Syncing model metadata (context window, RPM) into the DB — display info stays in `utils.ts`
- Removing models from the DB (handled reactively via 404 auto-disable)

---

## Design

### Free model detection
A model is "free" if its ID ends with `:free` OR if `pricing.prompt == "0"` AND
`pricing.completion == "0"` in the OpenRouter `/api/v1/models` response.

### New model default state
`model_config.enabled = false` — admin must explicitly enable in Model Manager after review.

### API key for sync
Uses `OPENROUTER_API_KEY` from environment (the system key, not per-user BYOK).
This is appropriate because syncing is a system/admin operation.
The public `/api/v1/models` endpoint works without auth, but using the key may
return a more complete list.

### Idempotency
`syncFreeModels()` is idempotent — safe to call multiple times. Only inserts
model IDs not already present in `model_config`. Never updates or deletes existing rows.

---

## Backend Implementation Plan

### 1. `FreeModelSyncService` (new — `com.openrouter.gateway.config`)

```java
@Service
public class FreeModelSyncService {

    public record SyncResult(int discovered, int added, List<String> newModelIds) {}

    @Transactional
    public SyncResult syncFreeModels() {
        // 1. GET https://openrouter.ai/api/v1/models
        // 2. Filter for free models (id ends with :free OR pricing = 0)
        // 3. Find model IDs not in model_config
        // 4. Insert new rows with enabled = false
        // 5. Evict enabledModels cache if any added
        // 6. Return SyncResult
    }
}
```

Dependencies: `HttpClient`, `ObjectMapper`, `ModelConfigRepository`,
`ModelConfigService` (cache eviction), `AppProperties` (API key + proxy base URL)

### 2. `AppStartupRunner` (new — `com.openrouter.gateway.config`)

```java
@Component
public class AppStartupRunner implements ApplicationRunner {
    // Calls freeModelSyncService.syncFreeModels() on startup
    // Non-fatal: catches all exceptions, logs WARN if unreachable
    // Logs SyncResult at INFO
}
```

### 3. `AdminController` — new endpoint

```
POST /api/admin/sync-models
```

Returns:
```json
{
  "discovered": 26,
  "added": 2,
  "newModelIds": ["new-provider/new-model:free", "..."]
}
```

---

## Frontend Implementation Plan

### `api.ts` — new method

```ts
adminApi.syncModels = () => api.post('/admin/sync-models')
```

### `ModelManagerPage.tsx` — "Sync Models" button

- Positioned in the header area next to filter tabs
- Shows spinner + "Syncing…" while in-flight
- On success toast:
  - If added > 0: "Found 2 new models — added as disabled. Review in Model Manager."
  - If added == 0: "All models up to date."
- Refreshes model list after sync

---

## Flyway Impact

None — new models are inserted via JPA (`modelConfigRepository.save()`), not Flyway.
Flyway V2 is the initial seed; ongoing model management is runtime data, not schema.

---

## Testing Plan

- Unit test `FreeModelSyncService` with mocked HTTP response
- Integration test: verify idempotency (running twice doesn't duplicate rows)
- Smoke test PowerShell script: `test-prd004-model-sync.ps1`

---

## Risks

| Risk | Mitigation |
|---|---|
| OpenRouter API down at startup | Non-fatal — log WARN, app starts normally |
| OpenRouter changes API response format | SyncService logs parse errors at WARN, continues |
| Free tier model IDs change format | Filter on both `:free` suffix and `pricing = 0` |
| Admin forgets to review new disabled models | Model Manager shows disabled count in filter tab |

---

## Implementation Notes (actual vs. planned)

### Dedup fix (post-ship bug — V7 migration)
OpenRouter returns some models in two forms: `X` (base, pricing=0) and `X:free` (explicit free variant). Both pass the free-model filter, producing two DB rows with the same display name. Fixed by:
1. `FreeModelSyncService.fetchFreeModelIds()`: drops `X` when `X:free` is also in the fetched list
2. `V7__cleanup_free_model_duplicates.sql`: removes existing base-ID rows that have a `:free` counterpart

### Flyway impact (minor deviation from plan)
V7 migration was added for the duplicate cleanup. New model inserts remain JPA-only (not Flyway).

### `app.openrouter.api-key` property
Added to `AppProperties.OpenRouter` (optional, empty default). Bound from `${OPENROUTER_API_KEY:}` in `application.properties` — same env var used by nginx, now also consumed by Spring Boot for the sync.

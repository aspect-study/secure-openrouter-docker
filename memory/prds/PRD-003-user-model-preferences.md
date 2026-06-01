# PRD-003 — User-Level Model Preferences

**Status:** COMPLETE  
**Created:** 2026-06-01  
**Updated:** 2026-06-01 — implemented Phases A–F; all backend, tests, and frontend delivered  
**Author:** aspect  

---

## Problem Statement

Currently, the list of available free models is controlled entirely by the admin via the Model
Manager. All users see the same set of enabled models with no ability to personalize which
models appear in their Playground.

Users have different preferences — some prefer faster, lighter models; others prefer larger,
more capable ones. Forcing every user to scroll through all globally-enabled models creates
unnecessary noise. There is no mechanism today for a user to curate their own model list.

---

## Goal

Give each user the ability to enable or disable models from their own Playground experience,
without affecting other users or the admin's global model configuration.

---

## Design Decisions

### Hierarchy: Admin Gates, User Filters

- Admin controls the **global allowlist** — a model disabled by admin is unavailable to
  everyone, regardless of user preferences.
- Users can only **filter down** from what admin has enabled — they cannot re-enable a
  globally-disabled model.
- If admin disables a model that a user had enabled in their preferences, that model
  disappears from that user's Playground automatically (enforced at query time via JOIN).

This keeps admin governance intact while giving users meaningful personalization.

### Default State: All Globally-Enabled Models Are On

When a user has no saved preferences for a model, it is treated as **enabled** for them.
This means new users see all globally-enabled models immediately without any setup required.
A `user_model_preferences` row is only written when a user explicitly toggles a model off
(or back on after having toggled it off). This keeps the table sparse.

### Immutable Conversation Model

Existing conversations are unaffected. A conversation stores its model at creation time
(`conversation.model` is immutable). Disabling a model only affects **new conversation
creation** — it will not appear in the model selector dropdown.

### ROLE_ADMIN Bypasses User Preferences

Users with `ROLE_ADMIN` always see all globally-enabled models regardless of any saved
preference rows. `UserModelPreferenceService.getEffectiveModels` must check the caller's
role and short-circuit to the full admin-enabled list when the caller is an admin.
This applies to all three endpoints. Admin preference rows (if any exist) are ignored.

---

## Scope

### In Scope

- New `user_model_preferences` table storing per-user model toggle state
- `GET /api/user/models` — returns user's effective model list (admin-enabled ∩ user-enabled);
  ROLE_ADMIN callers receive all globally-enabled models; full list, no pagination
- `PUT /api/user/models/{id}/toggle` — flip user preference; `{id}` is `model_config` integer PK
- `GET /api/user/models/{id}/status` — get a single model's status; `{id}` is `model_config` integer PK
- "My Models" settings page in the admin-ui for users to manage preferences
- Playground model dropdown uses user-scoped endpoint (not admin endpoint)
- If user disables all models: show a warning prompt in Playground rather than empty dropdown

### Out of Scope

- Users enabling globally-disabled models (not permitted by design)
- Admin viewing individual user model preferences
- Per-model preference notes or labels
- Bulk enable/disable all
- Orphaned preference row cleanup (see constraint note below)

---

## Schema Changes

### Flyway Migration Version

This feature requires the next unapplied Flyway migration after V4. At the time of writing,
**V5 is the correct version**. Before applying, verify no V5 migration exists:
```sql
SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```
If a V5 already exists for unrelated work, increment to V6.

### New Table: `user_model_preferences`

```sql
CREATE TABLE user_model_preferences (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  model_id   VARCHAR(150) NOT NULL,
  enabled    BIT(1)       NOT NULL DEFAULT b'1',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_model_pref (user_id, model_id),
  CONSTRAINT fk_ump_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

- One row per user per model — only written when user explicitly toggles.
- `enabled = 1` means the user wants this model visible; `enabled = 0` means hidden.
- Absence of a row means default (enabled), keeping the table sparse.
- `BIT(1)` required — Hibernate 6 / MySQL8Dialect maps Java `boolean` → `BIT`.
  Using `BOOLEAN` or `TINYINT` causes `SchemaManagementException` at startup.
- **No FK on `model_id`** — `model_config` rows can be removed without cascading deletes.
  Orphaned preference rows (for removed models) are functionally harmless: `getEffectiveModels`
  only returns models that exist in `model_config`, so orphaned rows are silently ignored.
  Cleanup can be performed via a future admin tool or migration if row count becomes a concern.
- `updated_at` is **DB-managed** via `ON UPDATE CURRENT_TIMESTAMP`. The application entity
  must NOT set this field manually (no `@PreUpdate`, no `updatedAt = now()` in service code).
  Hibernate should map it as `@Column(insertable = false, updatable = false)` or use
  `@UpdateTimestamp` only if the DB default is not relied upon — prefer DB ownership.

---

## New Components

### Backend

**`UserModelPreference`** entity (`preferences` package)

```java
@Entity
@Table(name = "user_model_preferences",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "model_id"}))
public class UserModelPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "model_id", nullable = false, length = 150)
    private String modelId;

    @Column(nullable = false)
    private boolean enabled = true;

    // DB-managed via ON UPDATE CURRENT_TIMESTAMP — do not set in application code
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
```

**`UserModelPreferenceRepository`**

```java
Optional<UserModelPreference> findByUserIdAndModelId(Long userId, String modelId);
List<UserModelPreference> findByUserId(Long userId);
void deleteByUserIdAndModelId(Long userId, String modelId);

// Atomic upsert — flip enabled in one DB round-trip, no load-or-create race
@Modifying
@Transactional
@Query(value = """
    -- Insert as disabled (b'0') because absence of row = enabled (sparse default).
    -- First explicit toggle always means "turn off". ON DUPLICATE KEY flips existing rows.
    INSERT INTO user_model_preferences (user_id, model_id, enabled)
    VALUES (:userId, :modelId, b'0')
    ON DUPLICATE KEY UPDATE enabled = NOT enabled
    """, nativeQuery = true)
void upsertToggle(@Param("userId") Long userId, @Param("modelId") String modelId);
```

**`UserModelPreferenceService`** (`preferences` package)

```
getEffectiveModels(Long userId, boolean isAdmin):
  1. Load all globally-enabled models from ModelConfig (admin allowlist)
  2. If isAdmin → return all globally-enabled models as UserModelDto (bypass user preferences)
  3. Load user's preference rows for their userId
  4. Build a map: modelId → UserModelPreference.enabled (absent = true by default)
  5. Filter: return only models where admin-enabled AND user-enabled
  6. Return as UserModelsResponse

toggleModel(Long userId, Long modelConfigId):
  1. Load ModelConfig by integer PK (modelConfigId) — not found → ModelNotFoundException (404)
  2. If ModelConfig.enabled = false → throw ModelAdminDisabledException (400,
     message: "This model is admin-disabled and cannot be toggled by users")
  3. Execute repository.upsertToggle(userId, modelConfig.getModelId()) — atomic, no load-or-create
     (INSERT ... ON DUPLICATE KEY UPDATE enabled = NOT enabled)
  4. Re-fetch the preference row to read current state
     NOTE: Re-fetch assumes single-node / read-from-primary. If replicas are introduced,
     ensure re-fetch routes to primary to avoid stale read returning pre-toggle state.
  5. Return updated UserModelStatusDto

  NOTE: Do NOT use load-or-create here. Two concurrent toggle requests would either both
  INSERT (unique constraint violation → 500) or both load-and-flip (last write wins → wrong
  state). The upsertToggle native query is atomic and handles both cases correctly.

getModelStatus(Long userId, Long modelConfigId, boolean isAdmin):
  1. Load ModelConfig by integer PK — not found → ModelNotFoundException (404)
  2. Load UserModelPreference by (userId, modelId) — enabled = true if absent
  3. If isAdmin → userEnabled = true, effectivelyEnabled = adminEnabled
  4. Return UserModelStatusDto {modelId, adminEnabled, userEnabled, effectivelyEnabled}
```

**`UserModelController`** (`preferences` package)

```
GET    /api/user/models             — effective model list for the authenticated user
PUT    /api/user/models/{id}/toggle — toggle user preference; {id} = model_config integer PK
GET    /api/user/models/{id}/status — single model status; {id} = model_config integer PK
```

**Why integer PK, not string modelId in path:** Model IDs (e.g., `meta-llama/llama-3.3-70b-instruct:free`)
contain forward slashes. Tomcat normalizes `%2F` before Spring MVC sees the request — URL-encoding
does not solve this without `ALLOW_ENCODED_SLASH=true`, a known path-traversal risk rejected by
Spring Security. Using the integer `model_config.id` as the path variable avoids encoding
entirely. The `modelId` string remains in response bodies for display; it never appears in a URL path.

Security rule (add to `SecurityConfig`):
```java
.requestMatchers("/api/user/models/**").hasAnyRole("USER", "ADMIN")
```

**`userId` is always resolved from the JWT principal — never accepted as a client parameter.**
All controller methods receive the authenticated user's email via `@AuthenticationPrincipal String email`,
look up the `User` entity to get `userId`, and pass it to the service. No endpoint exposes
a `userId` path or query parameter for preference operations.

**New exceptions:**

- `ModelNotFoundException` → 404 (modelId not found in `model_config`)
- `ModelAdminDisabledException` → 400 (user attempted to toggle an admin-disabled model)

Add handlers for both in `GlobalExceptionHandler`.

**Response DTOs**

`UserModelDto`:
```json
{
  "id": 3,
  "modelId": "meta-llama/llama-3.3-70b-instruct:free",
  "name": "Llama 3.3 70B Instruct",
  "adminEnabled": true,
  "userEnabled": true,
  "effectivelyEnabled": true
}
```

`id` is the `model_config` integer PK. The frontend uses this value in toggle/status API calls
(`PUT /api/user/models/{id}/toggle`). The `modelId` string is for display only and must never
be used as a URL path segment.

`UserModelsResponse` (for `GET /api/user/models`):
```json
{
  "models": [ ...UserModelDto ],
  "totalAdminEnabled": 12,
  "totalUserEnabled": 8
}
```

`totalUserEnabled` = **count of models effectively visible to the user** (admin-enabled ∩
user-enabled, accounting for sparse-row defaults). This is the number shown in the UI counter
"Showing 8 of 12 admin-enabled models." It is NOT the count of explicit `enabled = true` rows
in the database.

`UserModelStatusDto` (for toggle response and status endpoint):
```json
{
  "modelId": "meta-llama/llama-3.3-70b-instruct:free",
  "adminEnabled": true,
  "userEnabled": false,
  "effectivelyEnabled": false
}
```

### Playground Model Endpoint Change

`GET /api/chat/models` currently returns the admin-allowed list and remains unchanged.
The Playground frontend switches to calling `GET /api/user/models` instead.
Frontend change is localized to the model dropdown component in `PlaygroundPage.tsx`.

---

## Admin UI Changes

### New: "My Models" Page (user-facing)

Route: `/settings/models` or a tab within the existing Settings page.

Both the "My Models" warning banner and the Playground empty-state prompt are driven by
the same condition: **`models.filter(m => m.effectivelyEnabled).length === 0`**. This check
must be extracted into a shared utility or derived from a single `GET /api/user/models` call
to avoid drift between the two UI surfaces. Do not implement this check independently in
two places.

Layout:
- Page header: "My Models — customize which models appear in your Playground"
- Sub-header: "You can disable models you don't use. Admin-disabled models are grayed out
  and cannot be enabled."
- Toggle list — one row per globally-known model:
  - Model name + ID
  - Admin status badge: "Admin Enabled" (green) or "Admin Disabled" (gray, entire row dimmed)
  - User toggle switch — disabled (unclickable) if admin has disabled the model
  - Toggle calls `PUT /api/user/models/{id}/toggle` (using `UserModelDto.id`) with **optimistic UI**:
    - Immediately flip the toggle state in local React state before the API call resolves
    - On API success: no further action (state already correct)
    - On API failure: revert the toggle to its pre-click state and show an error toast
      (e.g., "Failed to update model preference. Please try again.")
- Counts: "Showing 8 of 12 admin-enabled models" (derived from `totalUserEnabled` /
  `totalAdminEnabled` in `UserModelsResponse`)
- Warning banner if `totalUserEnabled === 0`:
  "You've disabled all models. Enable at least one to use the Playground."

### Modified: Playground Page (`PlaygroundPage.tsx`)

- Model selector dropdown: call `GET /api/user/models` instead of `GET /api/chat/models`
- Filter dropdown to only `effectivelyEnabled: true` models
- If `models.filter(m => m.effectivelyEnabled).length === 0`: show inline prompt
  "All models are disabled. Go to Settings → My Models to enable at least one."
  with a link to the My Models page. Disable the send button.
- **Both this check and the "My Models" warning banner derive from the same API response
  shape — implement from a shared hook or utility, not independently.**

### Modified: Navigation / Settings

- Add "My Models" link in the sidebar under Settings section (user-visible only, not
  a separate admin nav item)

---

## Implementation Order

```
── Phase A: Infrastructure ──────────────────────────────────────────────────
1.  Verify last applied Flyway version; confirm V5 is next (or use V6 if needed)
2.  Flyway V5 migration: user_model_preferences table
3.  UserModelPreference entity (updated_at: insertable=false, updatable=false)
4.  UserModelPreferenceRepository

── Phase B: Exceptions ──────────────────────────────────────────────────────
5.  ModelNotFoundException (404)
6.  ModelAdminDisabledException (400)
7.  Register both in GlobalExceptionHandler

── Phase C: Service + API ───────────────────────────────────────────────────
8.  UserModelDto (with id field), UserModelStatusDto, UserModelsResponse DTOs
    (totalUserEnabled = effective visible count; id = model_config integer PK)
9.  UserModelPreferenceService:
      - getEffectiveModels(userId, isAdmin) — admin short-circuit
      - toggleModel(userId, modelConfigId) — reject admin-disabled (400); atomic upsert
      - getModelStatus(userId, modelConfigId, isAdmin)
10. UserModelController:
      - GET /api/user/models
      - PUT /api/user/models/{id}/toggle   ({id} = model_config integer PK)
      - GET /api/user/models/{id}/status   ({id} = model_config integer PK)
      - userId always from @AuthenticationPrincipal, never from request param
11. SecurityConfig: add .requestMatchers("/api/user/models/**").hasAnyRole("USER","ADMIN")

── Phase D: Unit Tests ──────────────────────────────────────────────────────
12. UserModelPreferenceServiceTest (JUnit 5 + Mockito):
      - getEffectiveModels: no preference rows → all admin-enabled returned
      - getEffectiveModels: user disables 2 → 2 excluded
      - getEffectiveModels: admin caller → all admin-enabled returned (preference ignored)
      - toggleModel: admin-disabled model → throws ModelAdminDisabledException
      - toggleModel: unknown modelConfigId → throws ModelNotFoundException
      - toggleModel: calls upsertToggle (not load-or-create) — verify via mock
      - toggleModel: idempotent (toggle twice → original state restored)
      - getEffectiveModels: admin removes model → orphaned pref row silently excluded

── Phase E: Frontend ────────────────────────────────────────────────────────
13. api.ts: add getUserModels(), toggleUserModel(id: number), getUserModelStatus(id: number) calls
    (all toggle/status calls use UserModelDto.id — integer — never the modelId string)
14. Extract shared useEffectiveModels() hook — used by both My Models page and Playground
15. My Models settings page:
      - toggle list, admin-disabled dimming
      - optimistic UI with revert + toast on failure
      - warning banner from shared hook (totalUserEnabled === 0)
16. PlaygroundPage.tsx: switch model dropdown to GET /api/user/models via shared hook
    - empty-state prompt from same hook condition
17. Sidebar navigation: add "My Models" link

── Phase F: Integration Validation ──────────────────────────────────────────
18. CLAUDE.md update: new endpoints, constraints, SecurityConfig rule
19. docker compose down -v && docker compose up (apply V5 migration cleanly)
20. Smoke: register user → disable 2 models → verify Playground dropdown excludes them
21. Smoke: admin disables a model user had enabled → verify it disappears for user
22. Smoke: user disables all models → Playground shows prompt, send disabled
23. Smoke: toggle endpoint idempotent (toggle twice restores original state)
24. Smoke: ROLE_ADMIN sees all globally-enabled models regardless of saved preferences
25. PRD-003 status → COMPLETE
```

---

## Key Constraints

- **Admin gates, user filters** — users can never re-enable an admin-disabled model;
  `toggleModel` must explicitly reject attempts with 400
- **ROLE_ADMIN bypasses user preferences** — `getEffectiveModels` short-circuits to full
  admin-enabled list when caller has ROLE_ADMIN; implemented in service, not controller
- **Flyway version** — V5 at time of writing; verify before applying (see Schema section)
- **Flyway boolean must be `BIT(1)`** — never use `BOOLEAN`/`TINYINT`
- **Never edit applied migrations** — V1–V4 locked; V5 is the next migration
- **Absence of row = enabled** — do not pre-populate rows for every user/model combination
- **`updated_at` is DB-managed** — do not set in application code; entity field must be
  `insertable = false, updatable = false`
- **`userId` from JWT only** — resolved via `@AuthenticationPrincipal String email` → user lookup;
  never accepted as a client-supplied path or query parameter
- **`totalUserEnabled` = effective visible count** — not the count of explicit DB rows
- **No FK on `model_id`** — orphaned rows are harmless; `getEffectiveModels` filters them
  out naturally via the `model_config` JOIN; cleanup is deferred
- **`/api/chat/models` stays unchanged** — Playground switches to `/api/user/models`
- **Empty-state condition shared** — `effectivelyEnabled count = 0` check must live in one
  place (shared hook); do not duplicate in My Models page and Playground independently
- **Optimistic UI must revert on failure** — toggle reverts to pre-click state on API error,
  accompanied by an error toast
- **SecurityConfig rule** — `/api/user/models/**` → `hasAnyRole("USER", "ADMIN")`
- **Existing conversations unaffected** — preferences only gate new conversation creation
- **All timestamps UTC** — `LocalDateTime.now(ZoneOffset.UTC)` throughout
- **No pagination on `GET /api/user/models`** — full list always returned; acceptable given
  bounded model count (~24 today). Revisit only if model count exceeds ~100.
- **Integer PK in path, never string modelId** — model IDs contain forward slashes incompatible
  with Spring MVC path variables; all toggle/status paths use `model_config.id` (integer)
- **`toggleModel` uses atomic upsert** — `INSERT ... ON DUPLICATE KEY UPDATE enabled = NOT enabled`;
  load-or-create is forbidden due to race condition under concurrent requests

---

## Open Questions (resolve before implementation)

1. Should the "My Models" page live as a tab within the existing Settings page, or as a
   separate top-level route? Tab is simpler; separate route is more discoverable.
2. Should we show admin-disabled models in the "My Models" page at all (dimmed), or hide
   them entirely? Showing them dimmed is more transparent — users understand why they're
   unavailable.
3. Should toggling off a model that is currently in use in an open Playground conversation
   do anything? Recommendation: no — the active conversation continues; the model just
   won't appear for new conversations.

# Learnings — 2026-06-01 Session (Controller Transaction + SSE Error Handling)

## 1. LazyInitializationException — missing @Transactional on controller methods

**What happened:** `GET /api/conversations/{id}` and `POST /api/conversations/{id}/messages`
threw `LazyInitializationException: failed to lazily initialize a collection of role:
com.openrouter.gateway.conversation.Conversation.messages: could not initialize proxy - no Session`.
Triggered when clicking "New Conversation" (which selects the new conversation, calling GET).

**Root cause:** Both controller methods called `conversationRepository.findByIdAndUserEmail()`
without a surrounding `@Transactional`. Spring opens a Hibernate session for the repository
call, then closes it immediately when the method returns. By the time `Detail.from(c)` called
`c.getMessages()` or `sendMessage` called `conversation.getMessages().add(userMsg)`, the session
was gone — lazy loading failed.

**Fix:**
- `@Transactional(readOnly = true)` on `get()` — keeps session open through `Detail.from(c)`
- `@Transactional` on `sendMessage()` — keeps session open through all `getMessages()` calls

**Rule:** Any controller method that calls `entity.getLazyCollection()` after loading via a
repository must be annotated `@Transactional`. The session boundary is the transaction boundary.
Using `@Transactional` on the controller is acceptable for blocking endpoints. For streaming
endpoints, the service layer already owns the transaction (ConversationService.streamMessage
is `@Transactional`).

**Note on sendMessage + HTTP call:** `sendMessage()` holds a DB connection open during the
OpenRouter HTTP call. For a blocking endpoint at this project's scale, this is acceptable.
At higher scale, move the business logic to a service method that loads data in one transaction
and makes the HTTP call outside it.

---

## 2. AuthorizationDeniedException logged as ERROR — expected admin probe

**What happened:** Every user login produced:
```
ERROR --- GlobalExceptionHandler : Unhandled exception: Access Denied
org.springframework.security.authorization.AuthorizationDeniedException: Access Denied
```

**Root cause:** `AuthProvider.tsx` probes `GET /api/admin/stats` after every login to detect
whether the user is an admin. Regular users will always get 403 from this endpoint — it is
expected, not a bug. But `AuthorizationDeniedException` fell through to the catch-all handler
in `GlobalExceptionHandler` which logged at ERROR with a full stack trace.

**Fix:** Added a dedicated `@ExceptionHandler(AuthorizationDeniedException.class)` that:
- Returns 403 JSON
- Logs at DEBUG (silent in production)
- Does not log the stack trace

**Rule:** Any exception that represents an expected, operational outcome (auth probe, rate limit,
not found) must have its own `@ExceptionHandler` that logs at an appropriate level. The catch-all
`Exception` handler must be a last resort for truly unexpected failures, logged at ERROR.

---

## 3. Upstream 429 in SSE stream — not sending error event to client

**What happened:** When OpenRouter returned 429 (e.g. Google AI Studio upstream rate limit),
the SSE stream threw `RuntimeException("OpenRouter stream error 429: ...")`. This was caught
by the generic `catch (Exception e)` block which:
1. Logged at ERROR (wrong — it's operational)
2. Called `emitter.completeWithError(e)` without first sending an SSE `error` event

The frontend received no `error` event, so the UI had no way to show the user a message.
The stream just silently closed.

**Fix:** Detect the 429 specifically by checking `e.getMessage().contains("stream error 429")`,
then:
1. Send `event: error` with a user-friendly message before `completeWithError()`
2. Log at WARN not ERROR

**Rule:** Any `completeWithError()` call in an SSE handler must be preceded by sending an
`event: error` SSE event — otherwise the client has no way to distinguish a clean close from
an error close. The frontend only receives events you explicitly send.

---

## 4. My Models tab — flat list vs. categorized list UX gap

**What happened:** `ModelManagerPage` (admin) showed models grouped by owner (NVIDIA, Meta,
Google, etc.) with emoji headers. `MyModelsTab` (user Settings) showed the same models as a
flat undifferentiated list. Users couldn't scan for their preferred provider.

**Fix:** Added `OWNER_GROUPS` + `groupModels()` to `MyModelsTab` (same pattern as
`ModelManagerPage`). Also added All / Enabled / Disabled filter tabs (identical to admin UI).

**Rule:** When the same data appears in multiple views, use a consistent presentation.
Diverging UX patterns for the same content create unnecessary cognitive load.
If grouping logic is needed in more than two places, extract it to a shared utility.

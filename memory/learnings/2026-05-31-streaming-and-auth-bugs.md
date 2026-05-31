# Learnings — 2026-05-31 Session (Streaming + Auth Bugs)

## 1. SSE protocol silently drops raw newlines

**What happened:** Tables and code blocks collapsed to a single line. The `\n` characters the model emitted were sent as raw `data: \n` in the SSE stream. The protocol treats a bare newline in a `data:` field as an event boundary, so the value received was an empty string.

**Fix:** JSON-encode every token (`objectMapper.writeValueAsString(token)`) so `\n` becomes `"\n"`. Decode with `JSON.parse(data)` on the frontend.

**Key detail:** SSE `data:` parsing must use `.replace(/^ /, '')` (strip one leading space per spec) — NOT `.trim()`. Trimming corrupts JSON strings that encode whitespace.

---

## 2. SseEmitter.complete() triggers Access Denied

**What happened:** Stream completed successfully (logs showed "SSE stream completed") but then `org.springframework.security.authorization.AuthorizationDeniedException: Access Denied` appeared on a Tomcat thread.

**Root cause:** `SseEmitter.complete()` causes Tomcat to perform an internal `ASYNC` dispatcher re-dispatch through the Spring Security filter chain. This re-dispatch carries no `SecurityContext` (it's an internal server event, not a new HTTP request), so Spring Security denied it.

**Fix:** `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` as the first rule in `authorizeHttpRequests`.

---

## 3. React auth hook independent state — the login redirect bug

**What happened:** After successful login, the user was immediately redirected back to `/login`. No error. No toast. Logs showed login succeeded.

**Root cause:** `useAuth()` used `useState` locally. Every component calling `useAuth()` got its own state. `LoginPage` called `setUser(authUser)` on its own instance. `ProtectedRoute` called `useAuth()` independently — its state was initialized from `loadUserFromStorage()` at mount time, before `localStorage` was written. Because `setUser` in a different component instance doesn't affect other instances, `ProtectedRoute` always saw `null`.

**Fix:** Lifted state to React Context (`AuthProvider`). One `useState` instance shared via `createContext` and `AuthContext.Provider`.

---

## 4. Axios 401 interceptor fires on wrong-password login

**What happened:** After fixing the auth context, login still failed — clicked Sign In, it spun briefly, then the form reset with no error message.

**Root cause:** The login endpoint returns 401 for wrong credentials. The Axios interceptor unconditionally called `window.location.href = '/login'` on any 401, causing a full page reload before the catch block in `LoginPage` could show a toast.

**Fix:** Guarded the interceptor: only redirect when `!isAuthEndpoint && hadToken`. Wrong credentials propagate normally to the catch block as an Axios error.

---

## 5. remark-gfm not included by default

**What happened:** Markdown tables rendered as raw pipe characters despite table component overrides being present in `ChatMessage`.

**Root cause:** `react-markdown` only supports CommonMark by default. GFM pipe tables require the `remark-gfm` plugin.

**Fix:** `npm install remark-gfm` + `remarkPlugins={[remarkGfm]}` on the `ReactMarkdown` component.

**Note:** After installing a new npm package, Vite dev server must be fully restarted (not just HMR). Clearing `node_modules/.vite` is also needed if the cache is stale.

---

## 6. Write tool produces null-byte-padded files

**What happened:** Repeated TypeScript "Invalid character" errors at a line past the actual file content. Files written by the Write/Edit tools in this environment contain null bytes (`\0`) as padding after the real content to fill a fixed buffer size.

**Fix:** After any Write/Edit that causes this, run:
```bash
tr -d '\0' < file > file.tmp && cp file.tmp file
# or for trailing single null:
truncate -s -1 file
```

**Critical:** `cat >>` (append) writes to the Linux mount path. The Edit/Write tools write to the Windows path. These appear to be the same file through the mount, but Vite (running on Windows) reads the Windows path. Edits via `cat >>` to fix truncation may not be seen by Vite if the Edit tool also holds a different cached version. Always verify with `hexdump -C file | tail -3` and use the Edit tool for the authoritative Windows-path write.

---

## 7. Frontend normalizeMarkdown must not run during streaming

**What happened:** During streaming, partial table content (e.g. just the header row before the separator arrives) was being passed through `normalizeMarkdown()`, which incorrectly injected separator rows or split cells in the wrong place, causing artifacts mid-stream.

**Fix:** Added `isStreaming` prop to `ChatMessage`. The `normalizeMarkdown()` call is skipped when `isStreaming === true`. On stream completion, the frontend switches to `normalizedContent` from the backend `done` event, which is the fully normalized persisted text.

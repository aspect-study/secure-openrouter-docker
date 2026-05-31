# ADR-013 — React Auth Context (AuthProvider) over Per-Component useState

**Date:** 2026-05-31  
**Status:** Accepted

## Context

`useAuth()` was implemented as a hook with local `useState`. Every component that called `useAuth()` received its own independent state instance. When `login()` ran in `LoginPage`, it updated LoginPage's copy. `ProtectedRoute` in `App.tsx` called `useAuth()` independently and always read a fresh `null` on mount, redirecting to `/login` immediately after a successful login.

## Decision

Convert `useAuth` to a React Context pattern:

- `useAuth.ts` — exports `AuthContext`, `AuthContextValue` interface, and `useAuth()` hook (calls `useContext`)
- `AuthProvider.tsx` — holds `useState`, implements `login()` and `logout()`, wraps children with `AuthContext.Provider`
- `main.tsx` — wraps `<App />` in `<AuthProvider>` inside `<BrowserRouter>`

## Rationale

- All components (`LoginPage`, `ProtectedRoute`, `PlaygroundPage`, `AdminLayout`) now read from one shared state instance.
- `login()` calling `setUser()` is immediately visible to `ProtectedRoute` on the next render.

## Consequences

- `AuthProvider` must be in `main.tsx` **outside** `<App>` so the context is available to route guards.
- The admin role probe inside `login()` uses raw `fetch` (not Axios) to bypass the global 401 interceptor — a USER hitting `/admin/stats` legitimately gets 401, and the interceptor was calling `window.location.href = '/login'` mid-login before `setUser()` could run.
- The Axios 401 interceptor is now guarded: only redirects when `!isAuthEndpoint && hadToken` — wrong credentials on the login form no longer trigger a page reload.

# ADR-006: Tailwind colors must use var() not hsl(var()) with shadcn radix-nova

**Date:** 2026-05-29
**Status:** Accepted

## Context

`npx shadcn@latest init` was run and chose the `radix-nova` style. This style generates CSS variables in `oklch()` format rather than the traditional `hsl()` format used by shadcn's `default` style.

Our `tailwind.config.ts` originally defined colors as:
```ts
background: 'hsl(var(--background))',
```

The CSS variable `--background` now contains an `oklch` value like `oklch(1 0 0)`.
The browser receives `background-color: hsl(oklch(1 0 0))` which is **invalid CSS** — all colors render as transparent/broken.

## Symptoms
- All UI components rendered with no background color
- Dark mode toggling appeared to have no effect
- Mobile layout `hidden md:flex` appeared broken (actually the color issue made it look broken)

## Decision

Change all Tailwind color definitions to use `var()` directly without the `hsl()` wrapper:

```ts
// Before (broken with oklch variables)
background: 'hsl(var(--background))',

// After (works with any CSS color format)
background: 'var(--background)',
```

## Why this works

`var(--background)` passes the raw CSS variable value to the property. Whether the variable contains `oklch(...)`, `hsl(...)`, or a hex value, the browser handles it natively. No wrapper function is needed.

## Trade-offs

- Slightly less explicit about expected format
- Works transparently with any CSS color format — future-proof if shadcn changes formats again

## When to revisit

If upgrading to Tailwind v4 (which uses CSS-first config), the entire `tailwind.config.ts` approach changes anyway.

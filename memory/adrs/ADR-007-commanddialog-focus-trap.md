# ADR-007: Use CommandDialog (not custom overlay) for keyboard-navigable command palette

**Date:** 2026-05-29
**Status:** Accepted

## Context

The AI Playground needs a `Ctrl+K` command palette for switching models with keyboard navigation (↑↓ arrows, Enter to select, Esc to close).

Two approaches were attempted:

**Attempt 1 — Custom div overlay:**
```tsx
{commandOpen && (
  <div className="fixed inset-0 z-50 ...">
    <div className="absolute inset-0 bg-black/60" onClick={close} />
    <div className="...panel...">
      <Command>
        <CommandInput ... />
        <CommandList>...</CommandList>
      </Command>
    </div>
  </div>
)}
```
Result: ↑↓ and Enter did NOT work. The custom overlay captured focus events before cmdk could handle them.

**Attempt 2 — CommandDialog without Command wrapper:**
```tsx
<CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
  <CommandInput ... />
  <CommandList>...</CommandList>
</CommandDialog>
```
Result: Nothing rendered. This `CommandDialog` implementation does NOT auto-wrap children in `Command`. `CommandInput` and `CommandList` had no `Command` parent.

**Attempt 3 (working) — CommandDialog with explicit Command wrapper:**
```tsx
<CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
  <Command>
    <CommandInput ... />
    <CommandList>...</CommandList>
  </Command>
</CommandDialog>
```

## Decision

Use `CommandDialog` wrapping an explicit `Command` component. This provides:
- Radix Dialog focus trap (keyboard events captured correctly)
- cmdk's internal keyboard navigation via the `Command` context
- Proper `autoFocus` on `CommandInput`
- `Esc` closes via Radix Dialog's built-in handler

## Key insight

The shadcn `radix-nova` `CommandDialog` is a thin wrapper — it provides `Dialog > DialogContent` but does NOT add a `Command` wrapper. Always wrap children in `<Command>` when using this implementation.

## Trade-offs

- Less visual control over the dialog appearance vs a fully custom overlay
- The `CommandDialog` style is opinionated — can be overridden with className

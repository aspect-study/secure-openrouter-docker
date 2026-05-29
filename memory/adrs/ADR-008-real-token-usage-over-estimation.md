# ADR-008: Display real token usage from OpenRouter, not client-side estimates

**Date:** 2026-05-29
**Status:** Accepted

## Context

The AI Playground header showed a token counter: `~X / 32K tokens`. This was calculated client-side using the heuristic "1 token ≈ 4 characters" applied to message content.

Problems with the estimate:
- 4 chars/token is a rough average for English prose — off by 20–50% for code, non-English text, special characters
- Context window sizes were hardcoded per provider prefix (guesses, not authoritative)
- Showed a "progress bar" implying precision that didn't exist
- The `32K` fallback was wrong for most models (many have 128K–200K context)

## Decision

Replace the estimate with real token counts from OpenRouter's API response.

OpenRouter returns `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens` in every response. The `ConversationController.sendMessage` response was updated to include these in the JSON payload. The frontend captures and displays them after each exchange.

Display format: `247 tokens last response · 18 in · 229 out`

## Implementation

1. `ConversationController.java` — added `parseUsage()` helper, included `usage` map in response body
2. `PlaygroundPage.tsx` — added `lastUsage` state, updated after successful message
3. Removed all client-side estimation code: `CONTEXT_WINDOWS`, `getContextWindow`, `promptTokens` state, the useEffect estimator
4. `Progress` bar component removed — no longer meaningful without accurate data

## Trade-offs

- Usage only shows after the first message (no pre-send estimate)
- Shows last-response tokens, not cumulative conversation total
- Does not show remaining context capacity (would require knowing actual model context window, which OpenRouter doesn't always return in a consistent field)

## When to revisit

If OpenRouter adds a `context_window_remaining` field to responses, we can show a progress bar again with real data.

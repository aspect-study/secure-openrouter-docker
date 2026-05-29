# ADR-009: Persist playground chat history to MySQL

**Date:** 2026-05-29
**Status:** Accepted

## Context

The AI Playground needed chat history. Two options:

1. **In-memory only** — history lives in React state, lost on page refresh. Simpler, no new DB tables.
2. **Persisted to MySQL** — conversations and messages saved to DB, survive refresh and can be loaded across sessions.

## Decision

Persist to MySQL via two new tables: `conversations` and `conversation_messages`.

## Reasons

- The project already has MySQL — no additional infrastructure cost
- Conversations are valuable data: they feed into admin chat logs and usage analytics
- Users returning after a refresh would lose context with in-memory only — poor UX for a playground meant for exploration
- The conversation endpoint naturally carries full message history to OpenRouter on each request, enabling proper multi-turn conversations (not just single-shot)

## Schema

```sql
conversations (
  id, user_email, title, model, created_at, updated_at
)

conversation_messages (
  id, conversation_id, role ENUM('user','assistant'), content MEDIUMTEXT, created_at
  FOREIGN KEY conversation_id → conversations(id) ON DELETE CASCADE
)
```

## Auto-titling

On the first user message in a conversation, the title is set to the first 60 characters of that message. This avoids requiring users to name conversations manually.

## Trade-offs

- `MEDIUMTEXT` for content supports up to 16MB per message — sufficient for long AI responses
- Full message history is sent to OpenRouter on every request (for context) — increases token usage as conversations grow
- No pagination on conversation messages — fine for typical conversation lengths, may need addressing for very long conversations

## When to revisit

If conversations grow very long (100+ messages), consider:
- Summarization-based context compression
- Sliding window (only last N messages sent to OpenRouter)
- Lazy loading messages in the UI

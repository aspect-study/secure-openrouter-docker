# ADR-014 — Three-Layer Markdown Quality Enforcement

**Date:** 2026-05-31  
**Status:** Accepted

## Context

Small free models (1–3B parameters) frequently output malformed GFM markdown: missing spaces around table cell pipes, separator rows merged onto the header line, missing blank lines before tables, concatenated words. These issues caused `remark-gfm` to fail silently and render raw pipe characters instead of formatted tables.

## Decision

Enforce markdown quality at three layers:

**Layer 1 — System prompt (source prevention)**  
`ConversationService.buildMessages()` prepends a `system` role message with explicit GFM rules to every OpenRouter request. The system message is not stored in `conversation_messages` and never appears in conversation history shown to the user.

**Layer 2 — Backend post-processor (MarkdownNormalizer)**  
`MarkdownNormalizer` (`@Component`) runs on the fully assembled response before `persistAssistantMessage()` in both the streaming and non-streaming paths. Operations: split merged header+separator rows, pad cells, inject missing separator rows, enforce blank lines before block elements. Non-fatal — normalization failure returns the original text.

**Layer 3 — Frontend (ChatMessage + normalizeMarkdown)**  
`ChatMessage` accepts `isStreaming` prop. Skips `normalizeMarkdown()` while streaming (partial content cannot be safely normalized). On stream completion, the frontend switches to `normalizedContent` from the `done` event payload (post-MarkdownNormalizer text). The frontend normalizer is a lightweight second pass for any remaining edge cases.

## Rationale

- System prompt prevents problems at the source — most effective for well-capable models.
- Backend normalizer ensures persisted text is always clean — reloading a conversation shows correct formatting.
- Frontend normalizer handles streaming artifacts that only appear in partial renders.

## Consequences

- `remark-gfm` npm package must be installed and passed as `remarkPlugins={[remarkGfm]}` to `ReactMarkdown`.
- `normalizeMarkdown()` must only run on complete content — running on partial markdown produces incorrect splits.
- `MarkdownNormalizer` is `@Component` — injected into `ConversationController` via constructor.
- The `done` SSE event includes `normalizedContent` field so the frontend bubble matches exactly what was persisted.

# PRD-005 — Gateway Intelligence Agent (ReAct Agent)

**Status:** SHIPPED — merged to `main` 2026-06-08  
**Created:** 2026-06-02  
**Author:** aspect  
**Related:** CCA-F exam prep, Phase 5

---

## Context

This feature is being built as a CCA-F (Anthropic Certified Claude Architect — Foundations)
exam study exercise. The CCA-F requires demonstrating a working ReAct agent with ≥2 tools
and verified handling of all `stop_reason` paths: `end_turn`, `tool_use`, and unexpected.

Since the project does not currently have an Anthropic API key, the agent is implemented
using **OpenRouter's free tier with an OpenAI-compatible tool use format**. A programmatic
mapping layer (`OpenRouterAdapter`) translates all wire-format differences so that the agent
service layer is written exclusively in Claude API terminology (`stop_reason`, `tool_use`
content blocks, `tool_result` blocks, `input_schema`). Swapping to the real Anthropic SDK
later requires changing only `OpenRouterAdapter`.

---

## Problem

The gateway admin has no conversational interface to query live system state. Checking
whether a model is enabled, or how much usage occurred today, requires navigating multiple
UI pages. An AI agent backed by real DB queries lets an admin ask natural-language questions
and get grounded, live answers.

---

## Goals

- Build a working ReAct agent that satisfies the CCA-F ≥2-tool requirement
- Write all agent logic in Claude API format so the code is directly useful for exam study
- Use existing OpenRouter infrastructure (no new API keys required)
- Make the ReAct loop visible in the UI so the reasoning chain is observable
- Handle all three `stop_reason` paths explicitly and verifiably

---

## Non-Goals

- General-purpose chat (this is separate from the existing Playground)
- Tool execution that writes/mutates data (read-only tools only)
- ~~Streaming agent responses~~ — **revised post-ship**: agent endpoint was converted to SSE to give real-time per-model retry feedback (see post-implementation notes below)
- Multi-user access (admin-only)

---

## Design

### LLM Backend

OpenRouter free tier via the existing nginx proxy, using the OpenAI-compatible
`POST /api/v1/chat/completions` endpoint with `tools` parameter.

Default model: `meta-llama/llama-3.3-70b-instruct:free` — best free-tier tool use support.
Admin can select any enabled model from the dropdown; non-tool-capable models will degrade
gracefully (agent returns `end_turn` immediately with a text answer).

Uses the admin's BYOK key (same as all other chat endpoints). Admin must have a key configured.

### The Claude ↔ OpenAI-compatible Mapping

This mapping is the primary CCA-F study value of the adapter layer:

```
Claude API format                  OpenAI-compatible format (OpenRouter)
─────────────────────────────────  ─────────────────────────────────────
stop_reason: "tool_use"        ←→  finish_reason: "tool_calls"
stop_reason: "end_turn"        ←→  finish_reason: "stop"
stop_reason: "max_tokens"      ←→  finish_reason: "length"

Tool definition:
  name, description,           ←→  type: "function"
  input_schema: {                   function: { name, description,
    type, properties, required }      parameters: { type, properties, required } }

Tool use in assistant message:
  { type: "tool_use",          ←→  choices[0].message.tool_calls[i]:
    id: "toolu_xxx",                  { id: "call_xxx",
    name: "...",                        function: { name: "...",
    input: { ... } }                    arguments: "{...}" }  ← JSON string, must parse

Tool result in next user message:
  { role: "user",              ←→  { role: "tool",
    content: [{                       tool_call_id: "call_xxx",
      type: "tool_result",            content: "..." }
      tool_use_id: "toolu_xxx",
      content: "..." }] }
```

### The Two Tools

#### `get_model_status`
- **input_schema:** `{ model_id: string }` (required)
- **returns:** `{ modelId, enabled, lastUsedAt }` or `{ error: "not found" }`
- **data source:** `ModelConfigRepository.findByModelId()`
- **CCA-F value:** demonstrates required parameter, tool result with structured data

#### `get_gateway_stats`
- **input_schema:** `{ date: string (YYYY-MM-DD, optional — defaults to today) }`
- **returns:** `{ date, totalRequests, totalTokens, topModel, activeUsers }`
- **data source:** `ChatLogRepository` (existing aggregate queries)
- **CCA-F value:** demonstrates optional parameter, tool result with numeric aggregates

Together they support queries like:
> "Is google/gemma-4-31b-it:free currently enabled, and how many requests were made with it today?"

This triggers both tools in sequence and exercises the multi-tool-call path.

### ReAct Loop (AgentService)

```
MAX_TURNS = 10  (safety guard)

loop:
  response = adapter.send(messages, toolDefinitions, apiKey)

  switch response.stopReason():
    case END_TURN  → return response.text()                    // ← path 1
    case TOOL_USE  →                                           // ← path 2
        results = executeTools(response.toolUseBlocks())
        messages += assistantMessage(toolUseBlocks)
        messages += userMessage(toolResultBlocks)
        continue loop
    default        → return "Agent stopped: " + stopReason    // ← path 3

if MAX_TURNS reached → return "Max turns reached."
```

---

## Backend Implementation Plan

### New package: `com.openrouter.gateway.agent`

```
agent/
├── AgentController.java              POST /api/agent/chat
├── AgentService.java                 ReAct loop
├── model/
│   ├── StopReason.java               enum: END_TURN, TOOL_USE, MAX_TOKENS, UNKNOWN
│   ├── AgentRequest.java             record: messages, model
│   ├── AgentResponse.java            record: reply, toolSteps
│   ├── ClaudeMessage.java            record: role, content (List<ContentBlock>)
│   ├── ContentBlock.java             sealed: TextBlock | ToolUseBlock | ToolResultBlock
│   └── ToolStep.java                 record: toolName, input, result (for UI)
├── tool/
│   ├── GatewayTool.java              interface: name(), description(), inputSchema(), execute(Map)
│   ├── GetModelStatusTool.java       injects ModelConfigRepository
│   └── GetGatewayStatsTool.java      injects ChatLogRepository
└── adapter/
    └── OpenRouterAdapter.java        Claude ↔ OpenAI translation + HTTP call
```

### Files to modify (existing)

| File | Change |
|---|---|
| `SecurityConfig.java` | Add `/api/agent/**` → `hasRole('ADMIN')` before catch-all |
| `application.properties` | Add `app.agent.default-model` property |
| `CLAUDE.md` | Document new endpoint, constraints, roadmap |

### No new dependencies, no new DB tables, no new Flyway migrations.

---

## Frontend Implementation Plan

### New file: `admin-ui/src/pages/admin/AgentPage.tsx`

- Chat input + scrollable message history
- **Tool steps rendered visually between messages** — collapsible panel per tool call:
  - Tool name + input JSON (what Claude asked for)
  - Result JSON (what the tool returned)
  - This makes the ReAct loop observable, which is the CCA-F study value
- Model selector dropdown (filters to admin-enabled models, defaults to Llama 3.3 70B)
- Labelled "Agent" or "Intelligence" in sidebar nav (admin-only)

### Files to modify (existing)

| File | Change |
|---|---|
| `api.ts` | Add `adminApi.agentChat(messages, model)` → `POST /api/agent/chat` |
| `App.tsx` / router | Add `/agent` route |
| `AdminLayout.tsx` | Sidebar nav entry (admin-only) |

---

## API Contract

### `POST /api/agent/chat`
**Auth:** ROLE_ADMIN + valid BYOK key  
**Produces:** `text/event-stream` (SSE — converted post-ship; see post-implementation notes)

**Request body:**
```json
{
  "question": "Is google/gemma-4-31b-it:free enabled and how much was it used today?",
  "model": "meta-llama/llama-3.3-70b-instruct:free",
  "history": [
    {"role": "user", "content": "How many models are enabled?"},
    {"role": "assistant", "content": "8 models are currently enabled."}
  ]
}
```
`model` optional. `history` optional — prior conversation turns for context continuity across model switches.

**SSE event sequence:**
```
event: status   data: {"type":"trying","model":"meta-llama/llama-3.3-70b-instruct:free","attempt":1,"total":12}
event: status   data: {"type":"skipped","model":"openai/gpt-oss-20b:free","reason":"rate_limited"}
event: done     data: {"reply":"...","toolSteps":[...],"modelUsed":"meta-llama/llama-3.3-70b-instruct:free"}
```

On failure:
```
event: error    data: {"error":"...","status":409|400|503|500}
```

**`done` payload:**
```json
{
  "reply": "Yes, google/gemma-4-31b-it:free is currently enabled...",
  "modelUsed": "meta-llama/llama-3.3-70b-instruct:free",
  "toolSteps": [
    {
      "toolName": "get_model_status",
      "input": { "model_id": "google/gemma-4-31b-it:free" },
      "result": { "modelId": "google/gemma-4-31b-it:free", "enabled": true, "lastUsedAt": "..." }
    },
    {
      "toolName": "get_gateway_stats",
      "input": { "date": "2026-06-08" },
      "result": { "totalRequests": 42, "totalTokens": 18500, "topModel": "google/gemma-4-31b-it:free" }
    }
  ]
}
```

---

## CCA-F Exam Coverage

| Requirement | How covered |
|---|---|
| ReAct agent with ≥2 tools | `GetModelStatusTool` + `GetGatewayStatsTool` |
| `stop_reason: tool_use` path | Multi-turn tool call loop in `AgentService` |
| `stop_reason: end_turn` path | Claude answers directly (e.g. "what is this gateway?") |
| Unexpected `stop_reason` path | `MAX_TOKENS` / `UNKNOWN` → graceful message with reason |
| Tool `input_schema` | Both tools define `properties` + `required` per JSON Schema |
| Tool result format | `tool_result` content blocks with `tool_use_id` linkage |
| Multi-tool chaining | Question that requires both tools exercises sequential tool calls |
| Claude API familiarity | `OpenRouterAdapter` documents every field mapping with comments |

---

## Risks

| Risk | Mitigation |
|---|---|
| Free model doesn't follow tool call protocol | Default to Llama 3.3 70B — known reliable; graceful degradation on `end_turn` |
| Infinite tool-call loop | MAX_TURNS = 10 hard limit |
| Admin has no BYOK key | AgentController returns 409 (same as chat — `KeyNotConfiguredException`) |
| OpenRouter tool_calls response deviates from spec | `OpenRouterAdapter` logs raw response at DEBUG; parse errors return 500 with message |
| Model removed from free tier mid-session | Standard 404 auto-disable fires; agent returns error on next request |

---

## Future: Swap to Anthropic SDK

When an `ANTHROPIC_API_KEY` is available, create `AnthropicAdapter implements AgentAdapter`
and bind it in place of `OpenRouterAdapter`. `AgentService` requires zero changes — it already
speaks native Claude format. The adapter interface isolates all wire-format details.

---

## Post-Implementation Notes (2026-06-07/08)

Three bugs surfaced after initial ship. All fixed and merged to `main`.

### Bug 1 — Default model doesn't support tool use
`nvidia/nemotron-nano-9b-v2:free` returned 404 from OpenRouter on tool-call requests.
**Fix:** Default model changed to `meta-llama/llama-3.3-70b-instruct:free`.
`OpenRouterAdapter` now maps `HttpClientErrorException.NotFound` → `ModelToolUseNotSupportedException`.

### Bug 2 — 429 rate-limiting with no fallback
When the chosen model hit OpenRouter's rate limit, the agent returned 500 with no retry.
**Fix:**
- `AgentService.run()` builds an ordered candidate list (requested model first, then all DB-enabled models via `ModelConfigService.getEnabledModelIds()`)
- Retry loop catches `ModelRateLimitedException` and `ModelToolUseNotSupportedException`, logs WARN, tries next candidate
- Exhausted list → `AllModelsUnavailableException` → 503
- `OpenRouterAdapter` has a 25 s read / 5 s connect timeout; `ResourceAccessException` (timeout) is treated as rate-limited
- `AgentResponse` gained a `modelUsed` field so the frontend knows which model actually answered

### Bug 3 — UX hung with no feedback during retry
While the backend silently tried multiple models, the frontend showed a static spinner.
**Fix:** `POST /api/agent/chat` converted from blocking JSON to SSE:
- Backend emits `event: status` before and after each model attempt (model name, attempt number, skip reason)
- Frontend uses `fetch` + `ReadableStream`; updates status bubble live ("Trying llama-3.3…", "rate-limited, trying next…")
- Toast notification when auto-switch occurs; model selector updates to the winner

### Bug 4 — Empty reply silently drops agent message
When a model's final turn was tool-only (no accompanying text), `finalReply` was `''`.
Frontend gated on `gotDone && finalReply` — falsy string meant nothing was added to chat.
**Fix:** Frontend now checks `gotDone` alone; empty reply renders a fallback string.

### Bug 5 — No conversation context after model switch
`AgentRequest` originally had only `question` + `model`. Each call started fresh.
When the retry loop switched to a different model, it had no knowledge of prior exchanges.
**Fix:** Added `AgentRequest.history: List<HistoryMessage>` (optional). Frontend sends all prior UI messages. `AgentService.runWithModel` prepends history into the `ClaudeMessage` list before the current question, so every retry candidate sees full context.

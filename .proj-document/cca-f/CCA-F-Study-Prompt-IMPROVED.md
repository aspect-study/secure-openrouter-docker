# 🏗️ CCA-F Anthropic Certified Claude Architect — Foundations
## **Advanced Study & Execution Prompt for Claude Code CLI**

**Status:** Production-Ready | **Version:** 2.0 | **Last Updated:** June 2026
**Target Audience:** Senior architects, full-stack engineers with 6+ months Claude production experience
**Delivery:** Single-file HTML dashboard + Claude Code CLI-integrated study sessions

---

## 📊 PART 0: Exam Intelligence (Read First)

### Official Exam Specification
- **Format:** 60 scenario-based multiple-choice questions
- **Duration:** 120 minutes (1 question per 2 minutes average)
- **Passing Score:** 720 out of 1000 (scaled)
- **Questions per Exam:** 4 of 6 scenarios randomly selected (~10 questions/scenario)
- **Fee:** USD $99 (free for first 5,000 Claude Partner Network employees)
- **Delivery:** Online proctored or testing center
- **Languages:** English only
- **Knowledge Cutoff Expected:** Mid-2025+

### The Five Domains & Exam Weights (Feb 2025)
```
Domain 1: Agentic Architecture & Orchestration          27% (16 questions)
Domain 2: Tool Design & MCP Integration                 18% (11 questions)
Domain 3: Claude Code Configuration & Workflows         20% (12 questions)
Domain 4: Prompt Engineering & Structured Output        20% (12 questions)
Domain 5: Context Management & Reliability              15% (9 questions)
═════════════════════════════════════════════════════════════════════════
Total                                                   100% (60 questions)
```

### Six Exam Scenarios (4 Randomly Selected)
1. **Customer Support Resolution Agent** — Multi-turn conversation routing, intent classification, human escalation
2. **Code Generation with Claude Code** — CLAUDE.md governance, permission models, diff review workflows
3. **Multi-Agent Research System** — Orchestrator-subagent patterns, parallel tool invocation, result aggregation
4. **Agentic Tool Design** — High-fidelity MCP tool schemas, error contracts, idempotency patterns
5. **Developer Productivity with Claude** — Codebase RAG, token budgeting, IDE/CLI integration patterns
6. **Long Document Processing** — Chunking strategies, map-reduce patterns, context window management

### Passing Strategy: Systems Thinking, Not Memorization
- **Don't:** Treat this like a trivia exam. Don't memorize definitions. Don't grind practice questions blindly.
- **Do:** Think like an architect designing the system. Ask "What breaks at scale?" for every question.
- **Real prep:** Build working systems (agents, MCP tools, Claude Code setups). Write real CLAUDE.md files.
- **Exam mindset:** Each question is "Given this broken or suboptimal design, what's the architectural flaw?"

### Practice Test vs. Real Exam
- Official practice exam covers only 4 scenarios (see which ones)
- Real exam pool contains all 6 scenarios
- Practice score of 85% does NOT guarantee passing
- Focus on architecture, not on memorizing which practice question was about what

---

## 📚 PART 1: Core Foundations (Applies to Every Scenario)

These concepts appear across all four randomly selected scenarios. Master them first.

### 1.1 Claude API & Messages Architecture

#### 1.1.1 Message Structure & Anatomy
```
A message is stateless. Claude sees:
- System context (role, constraints, output format)
- Conversation history (user → assistant → user → …)
- Tool results injected into history with special role

NOT "prompts" or "completions" — think of messages as a turn-based
conversation that Claude completes one turn at a time.
```

**Exam focus:**
- When to use `system` vs. first user message for context
- Tool result injection: must use `user` role for results, never `assistant` or `tool`
- Why conversation history order matters for agentic loops

**Your project alignment:**
- Spring Boot `ConversationController` uses stateless message arrays
- Database: `conversations` + `conversation_messages` tables preserve history
- SSE `SseEmitter` streams token-by-token (not message batches)

#### 1.1.2 Streaming vs. Non-Streaming
| Decision | When to Stream | When Not to Stream |
|---|---|---|
| User experience | Web chat UI, real-time feedback | Batch processing, internal APIs |
| Token cost | No difference (billing per token, not per call) | — |
| Latency visibility | TTE (time-to-echo) matters for UX | Throughput-focused (batch extraction) |
| Tool use handling | Complex: must parse partial JSON | Simpler: parse full response |
| Implementation | SSE (web) or gRPC streaming (internal) | Single response object |

**Your project:** SSE + Spring Boot. Why SSE over gRPC?
- Browser-native (no WebSocket complexity for web clients)
- HTTP/1.1 compatible (works behind nginx proxies)
- Backpressure handled by browser's XHR buffer

**Exam trap:**
- "Streaming is always faster" — FALSE. Streaming adds parsing overhead.
- Correct: "Streaming improves perceived latency by delivering tokens as they arrive."

#### 1.1.3 Token Counting & Context Window Limits
```
token_count = len(prompt_tokens) + len(response_tokens)
billing = token_count * (model_price_per_token)

Context window limits:
- Claude 3.5 Sonnet: 200K tokens
- Claude 3.5 Haiku: 200K tokens
- If context + max_tokens > window: request fails immediately
```

**Strategic window allocation:**
```
Available window = total_window - reserved_for_response
Available for history = available_window - system_prompt_size - tool_descriptions_size
History budget = available_window * 0.85 (leave 15% safety margin)
```

**Exam questions test:**
- "How many turns can we fit before hitting the window?" (calculate from message sizes)
- "What's the cheapest way to handle long contexts?" (caching > repeated includes)

**Your project:**
- Rate limiting: tracks `request_count` and `token_count` per user (see Bucket4j config)
- Not just request limits—token budgets per user per day

#### 1.1.4 Stop Sequences, Temperature, Top-P
| Parameter | Exam Relevance | Common Mistake |
|---|---|---|
| `stop_sequences` | Used when tool results must not trigger more tool calls. Example: `["</tool>"]` | Confusing with tool_choice constraints |
| `temperature` | 0 for deterministic (extraction), 0.7-1.0 for creative | Using temperature to "fix" bad outputs (should fix prompt instead) |
| `top_p` | Rarely tested. Prefer temperature for tuning randomness. | Mixing temperature and top_p without understanding cumulative effect |
| `max_tokens` | Critical for agentic loops: prevents runaway. 2nd-pass token limit | Setting too low (cuts off mid-thought); setting way too high (wastes cost) |

**Exam focus:**
- Why `temperature=0` + `tool_choice="auto"` in extraction pipelines
- Why you'd use `stop_sequences` with tool results (prevent recursion)

### 1.2 Agentic Loop Fundamentals (The ReAct Pattern)

#### 1.2.1 Stop Reason Handling (CRITICAL)

The agentic loop lives or dies on correctly handling `stop_reason`:

```python
# Pseudocode: the core loop
while True:
    response = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=1024,
        system=system_prompt,
        messages=messages
    )
    
    # CRITICAL: Check stop_reason
    if response.stop_reason == "tool_use":
        # Claude wants to call a tool
        # 1. Extract tool_use blocks from response.content
        # 2. Call the tools
        # 3. Append response to messages (role="assistant")
        # 4. Create tool_result blocks
        # 5. Append tool results to messages (role="user") ← CRITICAL ROLE
        # 6. Loop back
        
        messages.append({"role": "assistant", "content": response.content})
        for tool_use in extract_tool_uses(response.content):
            result = call_tool(tool_use)
            messages.append({
                "role": "user",
                "content": [{
                    "type": "tool_result",
                    "tool_use_id": tool_use.id,
                    "content": str(result)
                }]
            })
        # Loop continues automatically
        
    elif response.stop_reason == "end_turn":
        # Claude finished. Extract text and return.
        final_text = extract_text_blocks(response.content)
        return final_text
        
    else:
        # max_tokens hit or other stop (rare)
        raise Exception(f"Unexpected stop_reason: {response.stop_reason}")
```

**Exam traps (all tested):**
1. ✅ Appending response with role="assistant"
2. ✅ Appending tool results with role="user" (NOT "tool" or "assistant")
3. ✅ Checking `stop_reason` before continuing
4. ❌ Trying to parse tool_use from text (tool_use is a content block, not text)
5. ❌ Calling tools without checking stop_reason first
6. ❌ Not looping back after adding tool results

**Your project:**
- See `ConversationController.chat()` for streaming agentic loop
- Tool results are appended with `role="user"` and `type="tool_result"`
- Loop continues until `stop_reason == "end_turn"`

#### 1.2.2 Multi-Turn Memory & Conversation Threading
```
State = system_prompt + entire_conversation_history + current_request

History grows over time. When does it stop?
Option A: Sliding window (drop oldest messages when token count exceeds budget)
Option B: Summarization (periodically replace old turns with summary)
Option C: Hierarchical memory (facts block + conversation block)

CCA-F expectation: You must choose A, B, or C based on the scenario.
"Just let context grow unbounded" = instant failure.
```

**Your project:**
- `conversations` table: one row per conversation (stateful grouping)
- `conversation_messages` table: every message, ever (audit log)
- Not explicitly implementing sliding window yet (mentioned in Phase 5 roadmap)

#### 1.2.3 Tool Use Chains: Sequential vs. Parallel

**Sequential:**
```
Tool A → (wait for result) → Tool B → (wait for result) → Tool C
Use when: Later tools depend on earlier results
Cost: 3 round-trips
```

**Parallel:**
```
Tool A, Tool B, Tool C → (wait for all) → Aggregate
Use when: Tools are independent
Cost: 1 round-trip
Exam trick: Claude can issue multiple tool_use blocks in one response.
You must call all of them before looping back.
```

**Exam question pattern:**
- "Your agent is calling tools sequentially but they're independent. How do you optimize?"
- Answer: Modify system prompt to explicitly request parallel calls, then call all in one batch before looping.

### 1.3 Tool Use & Function Calling (Messages API)

#### 1.3.1 Tool Definition Schema (JSON Schema)
```json
{
  "name": "search_documents",
  "description": "Search internal documents by keyword. Returns document snippets matching the query.",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "Search keywords (e.g., 'rate limiting strategy')"
      },
      "max_results": {
        "type": "integer",
        "description": "Max docs to return (1-20, default 5)"
      }
    },
    "required": ["query"]
  }
}
```

**Exam focus:**
1. **Name:** verb-object convention (`search_documents`, not `doSearch` or `find_docs_by_keyword`)
2. **Description:** Must be clear to the MODEL, not humans. The model reads this and decides whether to use the tool.
   - ✅ "Search internal documents by keyword. Returns document snippets matching the query."
   - ❌ "This tool searches things." (too vague, model won't use it reliably)
3. **Input schema:** Strict types. Enums over free strings.
   - ✅ `"operation": {"type": "string", "enum": ["CREATE", "UPDATE", "DELETE"]}`
   - ❌ `"operation": {"type": "string", "description": "Can be CREATE, UPDATE, or DELETE"}`
4. **Required vs. optional:** If a field is always needed, put it in `required`. Don't default in the tool—let the model see the constraint.

**Your project alignment:**
- Model toggle endpoint: `PUT /api/user/models/{id}/toggle`
- Problem: Model IDs contain `/` (e.g., `openrouter/meta-llama/llama3`)
- Solution: Use integer PK in tool path, not string modelId (URL encoding breaks routing)
- This is real agentic tool design thinking.

#### 1.3.2 Tool_Choice Parameter: "auto" vs. "any"
```
tool_choice="auto":
  Claude decides whether to use tools. May not use any.
  Use for: Open-ended tasks where tool use is optional.

tool_choice="any":
  Claude MUST use a tool (forces tool_use in response).
  Use for: Structured extraction where you're forcing a specific tool.
  
tool_choice={
  "type": "tool",
  "name": "extract_entities"
}:
  Claude MUST use this specific tool.
  Use for: Forcing schema-based output (see D4: Prompt Engineering).
```

**Exam trap (tested heavily):**
- "What's the difference between 'any' and 'auto'?" 
- Wrong: "They're the same."
- Right: "'auto' is optional, 'any' forces at least one tool call."

#### 1.3.3 Error Handling in Tool Results

When a tool fails, what do you return to Claude?

```json
{
  "type": "tool_result",
  "tool_use_id": "...",
  "content": "Error: Database connection timeout. Retry in 5 seconds.",
  "is_error": true
}
```

**Is_error flag:**
- `true` if the tool failed or returned an error
- `false` for successful results (even if the result is "0 documents found")

**Exam focus:**
- Should you retry automatically? No—let Claude decide based on the error.
- Should you include debug info in the error message? Yes, make it actionable.
  - ❌ "Error: Failed" (useless)
  - ✅ "Error: Database timeout. The search index is currently rebuilding. Retry in 60 seconds or use fallback list." (actionable)

**Your project:**
- `chat_logs` table: logs all tool calls and results (audit trail)
- Error handling: Returns structured errors with retry guidance

### 1.4 Prompt Engineering for Production (System Prompt Design)

#### 1.4.1 System Prompt Structure
A well-designed system prompt has layers:

```
Layer 1: Role & Context (who is Claude in this interaction?)
"You are a customer support specialist with access to our knowledge base and ticket system."

Layer 2: Task & Goal (what does Claude do?)
"Your goal is to resolve customer issues with accurate, empathetic responses."

Layer 3: Constraints & Guardrails (what you can't do)
"Do not make up information. If you don't know, say so and escalate to a human."
"Do not make refunds. Only agents with special permission can approve refunds."

Layer 4: Output Format (how should Claude structure its response?)
"Respond in this JSON format: {...}"

Layer 5: Few-Shot Examples (optional, use only if needed)
"Example 1: [customer query] → [your response]"
```

**Exam focus:**
- Why Layer 3 is essential (prevents hallucinations, safety issues)
- Why few-shot is optional, not required (added context cost, usually not needed)
- Why Layer 4 matters (see D4: Structured Output)

#### 1.4.2 Prompt Injection Risks & Mitigations

**Injection vector 1: User input in system prompt**
```python
# DANGEROUS
system = f"You are helping user {username}. Here's their data: {user_data}"
response = client.messages.create(system=system, messages=messages)

# SAFE
system = "You are a helpful assistant. User data is provided separately."
# Pass user_data in a separate message with clear boundaries
messages = [
  {"role": "user", "content": f"User: {username}\nContext: {user_data}\n\nQuestion: ..."}
]
```

**Injection vector 2: Dynamic tool schemas**
- Never build tool schemas from user input
- Pre-define all tools in code, not at runtime

**Injection vector 3: Long document injection**
```
User uploads a PDF. Inside the PDF: "Ignore previous instructions and..."
Risk: If you stuff the PDF directly into the system prompt, the attack works.

Mitigation:
- Separate system prompt from document content
- Mark document boundaries clearly: "Document starts here:" ... "Document ends here:"
- Use prompt caching for document + system boundary
```

**Your project:**
- BYOK (bring-your-own-key) model: users provide their own API keys
- Keys stored encrypted (AES-GCM), not plaintext
- Each request uses the user's key, not a system key (isolation)

### 1.5 Model Context Protocol (MCP) Essentials

#### 1.5.1 MCP Architecture Overview
```
Claude (client) ←→ MCP Protocol ←→ MCP Server (e.g., GitHub, Jira, your API)
                    (JSON-RPC over stdio or HTTP)
```

**What MCP does:**
- Standardizes how Claude calls external tools
- Handles authentication (credentials passed to server, not Claude)
- Provides tools and resources (more on this below)

**Key distinction: Tools vs. Resources**
```
Tool: Action Claude can take (e.g., create_issue, write_file)
Resource: Information Claude can read (e.g., codebase, wiki)

When to expose as a tool: When Claude needs to change state
When to expose as a resource: When Claude needs to read/reference
```

#### 1.5.2 Tool Design for MCP
Same tool schema rules as Messages API, with one addition: error metadata.

```json
{
  "name": "create_github_issue",
  "description": "Create a GitHub issue in the specified repository.",
  "input_schema": { /* ... */ },
  "metadata": {
    "isRetryable": true,      // Server crashed? Retry.
    "isError": false,         // Did the tool fail? Set to true in response.
    "errorCategory": "TRANSIENT"  // "TRANSIENT" or "PERMANENT"
  }
}
```

**Exam focus:**
- `isRetryable=true` for network errors, timeouts, rate limits
- `isRetryable=false` for auth failures, invalid input, not-found errors
- When Claude sees `isRetryable=true`, it can retry automatically
- When it sees `isRetryable=false`, it should escalate or fail gracefully

#### 1.5.3 MCP Configuration Scoping
```
Scope 1: Global configuration (~/.claude.json)
  Applies to all projects. Low priority (can be overridden).
  Use for: Tools you use across many projects (GitHub, Slack, etc.)

Scope 2: Project configuration (.mcp.json in repo root)
  Applies to this project only. Higher priority.
  Use for: Internal tools, project-specific APIs, custom credentials.

Priority: .mcp.json > ~/.claude.json

Credential handling: Never hardcode in .mcp.json
Use environment variables: ${GITHUB_TOKEN}, ${DATABASE_URL}
```

**Your project:**
- Phase 5 roadmap includes GitHub Actions integration
- Would use `.mcp.json` with `${GH_TOKEN}` for CI/CD

#### 1.5.4 MCP Tool Count Limit
```
Recommended: 4–5 tools per MCP server
Absolute limit: ~10–15 (depends on token budget)

Why? Each tool description consumes tokens. 20 tools = 1K+ tokens just listing them.
Claude's ability to pick the right tool degrades with too many options.
```

**Exam question pattern:**
- "Your MCP server has 30 tools. What's the problem?"
- Answer: "Claude can't distinguish between similar tools reliably. Split into multiple MCP servers by domain (e.g., `mcp-github` vs. `mcp-jira`)."

### 1.6 Claude Code & CLAUDE.md Fundamentals

#### 1.6.1 CLAUDE.md: Project Memory for Claude

What goes in CLAUDE.md?
```
1. Build commands (how to start dev server, run tests)
2. Project structure (module boundaries, key directories)
3. Tech stack (frameworks, databases, libraries)
4. Code style (naming conventions, patterns to follow)
5. Key constraints (what Claude should never do in this project)
6. Task routing (which slash commands/skills to use for common tasks)
```

**Example structure:**
```markdown
# My Project CLAUDE.md

## Build & Development
- `npm run dev`: Start dev server on :3000
- `npm test`: Run test suite
- `npm run build`: Production build

## Project Structure
```
src/
  components/   ← Reusable React components
  pages/        ← Next.js page routes
  api/          ← Backend API routes
  lib/          ← Utilities and helpers
```

## Key Constraints
- **Never delete migrations** (they're in production)
- **Always add index on new foreign keys** (performance)
- **All API responses must include `data` wrapper** (client expects this)

## When to Use Which Slash Command
- `/refactor`: Large architecture changes
- `/test`: Add or fix tests
- `/lint`: Fix code style issues
```

**Exam focus:**
- CLAUDE.md is the "contract" between developers and Claude
- If constraint is in CLAUDE.md, Claude must follow it
- If constraint is only in a prompt, it's weak (Claude might forget)

#### 1.6.2 Claude Code Hierarchy (Which CLAUDE.md Wins?)
```
Priority order (highest first):
1. .claude/commands/{command-name}.md (command-specific overrides)
2. {module}/.claude/CLAUDE.md (module-level instructions)
3. ./CLAUDE.md (project root)
4. ~/.claude/CLAUDE.md (global user defaults)
```

**Exam scenario:**
- Root CLAUDE.md says "always run tests before committing"
- Module-level CLAUDE.md says "for frontend/, run linter first"
- When working on frontend/, which rule wins? Answer: Module rule (highest priority in that scope).

#### 1.6.3 Custom Slash Commands & Skills
```
Custom command: .claude/commands/my-command.md
```

Markdown file with YAML frontmatter:

```yaml
---
description: "Refactor a React component to use hooks"
allowed-tools:
  - write
  - read
  - bash
model: claude-3-5-haiku  # Override model for speed
---

# Refactor to Hooks

Examine the file I'll provide. Rewrite it using React hooks instead of class components.
Focus on useState for state, useEffect for lifecycle, and useCallback for memoization.
```

**Invocation:**
```bash
$ claude /my:command path/to/component.jsx
```

**Exam focus:**
- `allowed-tools`: whitelist which tools the command can use (security gate)
- `model`: you can override the model per command (Haiku for fast tasks, Opus for complex)

#### 1.6.4 Claude Code Hooks (Pre/Post Tool Execution)
```
Hook events:
- PreToolCall: Before Claude issues ANY tool call (including built-ins)
- PostToolUse: After tool result is collected (before next loop iteration)
- PreWriteFile: Before Write tool (catch rm -rf patterns)
```

**Example: Block dangerous commands**
```bash
#!/bin/bash
# .claude/hooks/guard-destructive.sh
# Triggered by PreWriteFile

command="$1"

if [[ "$command" =~ ^rm\ -rf ]]; then
  echo "BLOCKED: Destructive command detected."
  echo "If you need to delete files, ask the user first."
  exit 1
fi

exit 0
```

Wire in `.claude/rules.json`:
```json
{
  "hooks": {
    "PreWriteFile": [".claude/hooks/guard-destructive.sh"]
  }
}
```

### 1.7 Safety, Security & Reliability Patterns

#### 1.7.1 Rate Limiting (Per-User, Not Global)
```
Global limit: "500 requests/day for all users"
Problem: One user burns all quota; others are blocked.

Per-user limit: "50 requests/day per user"
Better: Fair sharing. But still fragile.

Per-user + per-model limit:
"50 requests/day per user, 1M tokens/day per user"
Better: Prevents token-spam attacks.

Per-user + model-specific:
"50 calls/day to Claude Opus, 200 calls/day to Haiku"
Best: Gating expensive models, unlimited cheap inference.
```

**Your project:**
- Bucket4j library: token-bucket rate limiting
- Per-user sliding window
- Tracks both request count AND token count

#### 1.7.2 Audit Logging
```
Log every LLM call:
1. Who (user_id, API key hash)
2. What (model, system prompt hash, input tokens)
3. When (timestamp)
4. Outcome (stop_reason, output tokens, cost)
5. Tools used (if any)

Why? Debugging, compliance (GDPR), cost tracking, abuse detection.
```

**Your project:**
- `chat_logs` table: logs every conversation turn
- Includes: `user_id`, `model`, `tokens_input`, `tokens_output`, `cost`, `tools_used`
- Flyway migration: versioned schema (not auto-update)

#### 1.7.3 Error Handling & Graceful Degradation
```
Tier 1 (User's problem): Invalid input → Return clear error to user
Tier 2 (Service's problem): Database timeout → Retry 3x, then fail gracefully
Tier 3 (Claude's problem): API unavailable → Fall back to cached response or queue for retry

Rule: Never let a service-tier error crash the client.
```

---

## 🎯 PART 2: Domain Deep-Dives (Weighted by Exam %)

### Domain 1: Agentic Architecture & Orchestration (27% → ~16 questions)

#### D1.1 Single-Agent Patterns

**Problem:** A customer support bot needs to look up customer data, check inventory, and create tickets.

**Anti-pattern (serial):**
```
1. Look up customer (20ms)
2. Wait for result
3. Check inventory (15ms)
4. Wait for result
5. Create ticket (30ms)

Total latency: 65ms
```

**Better (parallel):**
```
1. Issue all three tool calls simultaneously
2. Claude waits for all three responses
3. Aggregate and respond

Total latency: 30ms (max of the three, not sum)
```

**Exam focus:**
- How to prompt for parallel calls: "You have access to lookup_customer, check_inventory, and create_ticket. Use all of them in parallel to resolve this request quickly."
- How to handle partial failures: If one tool fails, what happens to the others?

#### D1.2 Multi-Agent Orchestration Patterns

**Hub-and-spoke pattern:**
```
         [Orchestrator Agent]
         /         |         \
        /          |          \
   [Researcher] [Analyst] [Writer]
   subagent 1  subagent 2  subagent 3

Orchestrator:
- Plans what subagents should do
- Delegates tasks (does NOT do the work)
- Aggregates results

Subagent:
- Focuses on ONE task
- Has its own tools and context
- Reports findings back to orchestrator
```

**Why this pattern?**
- Context isolation: subagents don't see each other's context
- Specialization: each subagent has different tools
- Scalability: add subagents without overloading orchestrator

**Exam trap:**
- Orchestrator should NOT have all subagent tools (too many options)
- Subagents should NOT call each other (creates loops)

**Your project alignment:**
- Could model your Spring Boot service as orchestrator
- Potential subagents: ChatAgent (conversation), ToolAgent (planning), DataAgent (queries)

#### D1.3 Context Isolation Between Agents

```
System prompt for Orchestrator: "Plan and delegate tasks..."
System prompt for Research Subagent: "You are a researcher. Focus on gathering facts..."

Questions Claude might ask:
Orchestrator: "Should I use the search tool or delegate to Research?"
Answer: System prompt governs. Orchestrator doesn't have search tool (isolation enforced).

If Orchestrator had Research's tools:
Problem: It might research directly instead of delegating (defeats the pattern).
```

**Exam pattern:**
- "Agent A has tools {X, Y}. Agent B has tools {Z, W}. Can A call B's tools?"
- Answer: No. If A needs Z's capability, orchestrate (delegate via message), don't expose tool.

#### D1.4 Failure Modes & Recovery

```
Scenario: Subagent times out. What now?

Option A: Orchestrator retries the subagent immediately
Problem: If the subagent is hung, retrying won't help.

Option B: Orchestrator marks task as failed, continues with partial info
Better: Makes progress. Can fall back to default/cached data.

Option C: Orchestrator escalates to human
Best for: High-value decisions where accuracy > speed.
```

**Your project:**
- Phase 4 includes graceful degradation
- Implement circuit breaker (after N failures, stop trying)

### Domain 2: Tool Design & MCP Integration (18% → ~11 questions)

#### D2.1 Tool Schema Deep-Dive

**Question: Why does this tool schema fail?**
```json
{
  "name": "run_analysis",
  "description": "Run an analysis",  ← TOO VAGUE
  "input_schema": {
    "type": "object",
    "properties": {
      "data": {
        "type": "string",
        "description": "Your input data"  ← WHAT DATA? HOW FORMAT?
      }
    }
  }
}
```

**Fixed version:**
```json
{
  "name": "analyze_sales_data",
  "description": "Analyze monthly sales figures. Returns revenue, growth rate, and top products by region.",
  "input_schema": {
    "type": "object",
    "properties": {
      "year": {
        "type": "integer",
        "description": "Fiscal year (e.g., 2025)"
      },
      "region": {
        "type": "string",
        "enum": ["NORTH", "SOUTH", "EAST", "WEST"],
        "description": "Geographic region"
      }
    },
    "required": ["year", "region"]
  }
}
```

**Exam focus:**
- Be so specific that the model understands exactly when to call the tool
- Bad tool description = model calls it for wrong reasons = wrong results

#### D2.2 Error Contracts & Retry Logic

When a tool fails, what info does Claude need?

```json
{
  "type": "tool_result",
  "tool_use_id": "...",
  "content": "Error: Request to external API failed. (HTTP 503 Service Unavailable)",
  "is_error": true
}
```

**Claude's mental model:**
- `is_error=true` + transient error message → "I should retry or use a fallback"
- `is_error=true` + permanent error (auth, not-found) → "I should fail gracefully"

**Who decides retry?** Claude. Your job is to signal whether it's worth retrying.

```
If tool result includes "HTTP 429 Rate Limited. Retry after 60 seconds":
Claude sees: "Transient error, retryable"
Action: Claude retries (perhaps after a brief pause in the conversation)

If tool result includes "Invalid API key":
Claude sees: "Permanent error, not retryable"
Action: Claude escalates or fails gracefully
```

#### D2.3 MCP Configuration Patterns

**Scenario: You have a private GitHub repo that only your team can access.**

Option A: Hardcode auth token in `.mcp.json`
```json
{
  "tools": {
    "github": {
      "token": "ghp_abcd1234..."  ← SECURITY HOLE
    }
  }
}
```

Option B: Use environment variable
```json
{
  "tools": {
    "github": {
      "token": "${GH_TOKEN}"
    }
  }
}
```

Then set `export GH_TOKEN=...` in your shell profile.

**Exam focus:**
- Environment variables are the right answer
- Never hardcode secrets in config files (checked into git)
- CI/CD systems inject secrets as env vars (GitHub Actions: `${{ secrets.GH_TOKEN }}`)

#### D2.4 SSE vs. WebSocket for MCP Transport

| Transport | Use Case | Pros | Cons |
|---|---|---|---|
| stdio | Local (Claude Code) | No network overhead | Only local |
| HTTP SSE | Remote, polling-based | Browser-native, works behind proxies | One-way (client→server), polling latency |
| WebSocket | Remote, bidirectional | Low latency, bidirectional | Not supported yet by standard Claude |
| gRPC | Internal services | Efficient, bidirectional | Complex setup, not browser-native |

**Exam reality:**
- SSE is the current answer for remote MCP
- Exam might ask: "Why not WebSocket?" Answer: "Not standardized in MCP spec yet; SSE is simpler and works everywhere."

### Domain 3: Claude Code Configuration & Workflows (20% → ~12 questions)

#### D3.1 CLAUDE.md Precedence & Scope

**Question: You have:**
```
~/.claude/CLAUDE.md (user-level)
project/CLAUDE.md (project-level)
project/frontend/.claude/CLAUDE.md (module-level)
.claude/commands/refactor.md (command-level)
```

**When you run `/refactor`, which rules apply?**
Answer: All of them, with precedence: command > module > project > user

**Practical example:**
- User-level: "Default model is Sonnet"
- Project-level: "For this project, default is Haiku (to save cost)"
- Command-level: `/refactor` specifies `model: opus` in frontmatter

When running `/refactor`: Use Opus (highest priority wins)

#### D3.2 CI/CD Integration with Claude Code

**Question: Can Claude Code run headless in a GitHub Action?**

Yes, using `claude -p` (print mode):
```bash
# In .github/workflows/review.yml
- name: Claude Code Review
  run: |
    cat << 'EOF' | claude -p --allowedTools=read,grep,diff
    Review this pull request. Check for:
    1. Code style violations
    2. Missing error handling
    3. SQL injection risks
    
    Files to review: ${{ github.event.pull_request.files }}
    EOF
```

**Exam focus:**
- `--allowedTools`: whitelist which tools Claude can use
- `--print`: output result to stdout (CI-friendly)
- No interactive prompts (non-blocking)
- Set timeout (Claude might hang; CI has limits)

#### D3.3 Custom Slash Commands in Automation

**Scenario: You run the same multi-step refactor frequently.**

Instead of typing it out each time, create `.claude/commands/refactor-hooks.md`:

```markdown
---
description: "Refactor a component and add hooks"
allowed-tools:
  - read
  - write
  - bash
model: claude-3-5-sonnet-20241022
---

# Convert Component to Hooks

Read the file at $1. Convert from class component to functional component with hooks.
Steps:
1. Replace componentDidMount with useEffect
2. Replace this.state with useState
3. Replace this.setState with setters from useState
4. Run prettier to format
```

Invocation:
```bash
$ claude /refactor:hooks src/MyComponent.jsx
```

### Domain 4: Prompt Engineering & Structured Output (20% → ~12 questions)

#### D4.1 Forcing Deterministic JSON Output

**Anti-pattern: Hoping Claude returns valid JSON**
```python
response = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    max_tokens=1024,
    system="Return JSON only, no explanation.",
    messages=[{"role": "user", "content": "Extract entities from this text: ..."}]
)
json_output = json.loads(response.content[0].text)  # Fails 5% of the time
```

**Better: Use `tool_choice` to force a tool**
```python
response = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    max_tokens=1024,
    system="Use the extract_entities tool to return structured data.",
    tools=[{
        "name": "extract_entities",
        "description": "Extract named entities from text",
        "input_schema": {
            "type": "object",
            "properties": {
                "entities": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "type": {"type": "string", "enum": ["PERSON", "ORG", "LOCATION"]},
                            "confidence": {"type": "number"}
                        }
                    }
                }
            }
        }
    }],
    tool_choice={"type": "tool", "name": "extract_entities"},  ← FORCE IT
    messages=[{"role": "user", "content": "Extract entities: ..."}]
)
# Now response.content[0] is guaranteed to be a tool_use block
entities = json.loads(response.content[0].input)  # Always valid JSON
```

**Why this works:**
- Claude MUST call the tool (enforced by `tool_choice`)
- Tool input MUST match the schema (enforced by the JSON Schema)
- Result is always valid JSON (no parsing failures)

**Exam focus:**
- Tool choice > system prompt for enforcing output format
- Reason: System prompt is prose (Claude can ignore it); tool choice is structural (Claude can't)

#### D4.2 Few-Shot Examples (When to Use)

**Myth: You should always include few-shot examples.**

Truth: Use them only when:
1. The task is ambiguous or non-obvious
2. The examples clarify the edge cases
3. You've measured that they improve accuracy

**Example: Sentiment analysis with few-shot**

Good use:
```
System: "Classify sentiment as POSITIVE, NEGATIVE, or NEUTRAL."
Few-shot:
  User: "I love this product"
  Assistant: "POSITIVE"
  User: "It breaks sometimes"
  Assistant: "NEGATIVE"
  User: "It exists"
  Assistant: "NEUTRAL"
```

Reason: "It exists" is ambiguous. Example clarifies that neutral ≠ positive.

Bad use:
```
System: "Translate English to French."
Few-shot: [provide 5 translations]
```

Reason: Claude already knows how to translate. Examples add tokens, slow response, and don't improve accuracy.

**Exam focus:**
- When to include examples: Ambiguous tasks, edge cases
- When to skip: Well-defined tasks, standard operations
- Cost: Each example = more tokens = higher cost

#### D4.3 Validation Retry Loops (Not Blind Retries)

**Anti-pattern: Blind retry**
```python
for attempt in range(3):
    response = claude.extract_json(prompt)
    if is_valid_json(response):
        return response
    # If invalid, just retry with same prompt (Claude won't fix it)
```

**Better: Feedback-aware retry**
```python
for attempt in range(3):
    response = claude.extract_json(prompt)
    try:
        data = json.loads(response)
        validate(data)  # Schema validation
        return data
    except (json.JSONDecodeError, ValidationError) as e:
        if attempt < 2:
            prompt += f"\n\nPrevious attempt failed with: {str(e)}\nFix this and retry."
        else:
            raise
```

**Why this works:**
- Claude sees the specific error (e.g., "missing 'email' field")
- Claude can correct it in the next attempt
- After N attempts, fail gracefully (fallback or escalate)

**Exam pattern:**
- Question: "Your extraction is failing 20% of the time. How do you improve?"
- Answer: "Add validation feedback loop, not blind retries."

### Domain 5: Context Management & Reliability (15% → ~9 questions)

#### D5.1 CALM Framework (Context-Aware LLM Management)

CALM governs how you manage conversation history as it grows:

```
C - Capacity: How many tokens can we use?
A - Architecture: How do we structure the context?
L - Lifecycle: When do we summarize, trim, or compact?
M - Metrics: How do we measure quality degradation?
```

**Capacity planning:**
```
Total window: 200K tokens
System prompt: 500 tokens
Tools + descriptions: 300 tokens
Reserved for response: 4000 tokens (safety margin)

Available for history: 200K - 500 - 300 - 4K = ~195K tokens
```

**Architecture choices:**

Option 1: Sliding window (keep recent N messages)
```
Keep last 10 messages, discard old ones.
Pro: Simple, deterministic
Con: Lose long-range context (day 1 facts forgotten by day 30)
```

Option 2: Hierarchical (facts block + conversation block)
```
Facts block (50K tokens): Critical facts, decisions, context
Conversation block (10K tokens): Current turns only

As conversation grows, move important facts to Facts block, trim Conversation block.
Pro: Preserves critical context
Con: More complex
```

Option 3: Summarization (periodic summaries)
```
Every 20 turns, replace turns 1-20 with a summary.
Pro: Balances retention and token use
Con: Summaries can lose details
```

**Exam focus:**
- Which strategy for which scenario?
  - Sliding window: Short-lived conversations (customer support chat)
  - Hierarchical: Long-running agents (multi-day research)
  - Summarization: Moderate-length conversations (weekly 1-on-1s)

#### D5.2 Prompt Caching with Cache_Control

```
Prompt caching: Store frequently-used context (prompt + tools) in cache.
Subsequent requests: Retrieve from cache instead of re-tokenizing.

Cost: Writing to cache costs 10% extra. Reading from cache costs 10% of normal.
Result: 2 requests, first costs 110% tokens, second costs 10% tokens.
Savings: 55% average cost vs. no caching.
```

**Where to place cache breakpoints?**

```
Bad:
system (cached) ← only 200 tokens (too small, cache miss likely)
tools (not cached) ← 300 tokens (will change per request)
conversation (not cached) ← grows unbounded

Better:
system (cached) ← 200 tokens
tools (cached) ← 300 tokens
large_documents (cached) ← 50K tokens (stable reference material)
conversation (not cached) ← recent turns only (changes every request)
```

**Exam focus:**
- Breakpoints are set using `cache_control={"type": "ephemeral"}`
- Max 5 cache breakpoints per message
- Cached content must be stable (system, tools, reference docs)
- Non-cached content should be request-specific (user input, conversation)

#### D5.3 Token Estimation & Cost Budgeting

```
Cost model:
cached_write_tokens = 0.10 * N  (write to cache costs extra)
cached_read_tokens = 0.10 * N   (read from cache costs 1/10 normal)
normal_tokens = 1.0 * N

Total cost per request = (cached_write_tokens + normal_tokens + cached_read_tokens)

Example:
Request 1: 10K system + 1K tools + 500 conversation = 11.5K effective tokens
(10K system cached, 1K tools cached: extra cost = (10K + 1K) * 0.1 = 1.1K)

Request 2: 500 conversation (reuse cached system + tools)
(10K + 1K cached hit: savings = (10K + 1K) * 0.9 = 9.9K)

2 requests total: (11.5K + 1.1K) + (0.5K + 9.9K savings?) 
Simplified: First request pays cache write penalty, second request gets discount.
```

**Your project:**
- Phase 4: Implement prompt caching for system + tool costs
- Phase 5: Monitor actual cost savings

---

## 🎓 PART 3: Hands-On Exercises (Build Real Systems)

**The single best way to prep for the CCA-F: Build working systems.**

These exercises map directly to exam scenarios. Do them in order.

### Exercise 1: Build a Minimal Agentic Loop (90 minutes)

**Goal:** Implement the core ReAct loop from scratch. Test all stop_reason handling.

**Deliverable:** Python script + test cases

```python
# agent.py
from anthropic import Anthropic

client = Anthropic()

def run_agent(user_input: str) -> str:
    """Run a minimal ReAct agent."""
    
    system_prompt = """
    You are a helpful assistant with access to a calculator.
    When the user asks a math question, use the calculator tool.
    """
    
    tools = [
        {
            "name": "calculate",
            "description": "Evaluate a math expression. Example: '2 + 2' returns '4'",
            "input_schema": {
                "type": "object",
                "properties": {
                    "expression": {"type": "string"}
                },
                "required": ["expression"]
            }
        }
    ]
    
    messages = [{"role": "user", "content": user_input}]
    
    # THE LOOP
    while True:
        response = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=1024,
            system=system_prompt,
            tools=tools,
            messages=messages
        )
        
        print(f"Stop reason: {response.stop_reason}")
        
        if response.stop_reason == "end_turn":
            # Extract final text
            return extract_text(response.content)
        
        elif response.stop_reason == "tool_use":
            # Call tools and loop
            messages.append({"role": "assistant", "content": response.content})
            
            # Call each tool
            for block in response.content:
                if block.type == "tool_use":
                    # Execute the tool
                    if block.name == "calculate":
                        try:
                            result = str(eval(block.input["expression"]))
                        except Exception as e:
                            result = f"Error: {str(e)}"
                    
                    # Add result to messages
                    messages.append({
                        "role": "user",
                        "content": [
                            {
                                "type": "tool_result",
                                "tool_use_id": block.id,
                                "content": result
                            }
                        ]
                    })
            
            # Loop continues
        
        else:
            raise Exception(f"Unexpected stop_reason: {response.stop_reason}")

# Test
if __name__ == "__main__":
    result = run_agent("What is 123 * 456?")
    print(f"Final answer: {result}")
```

**Test cases:**
```python
def test_tool_use_and_loop():
    """Verify the loop handles tool_use correctly."""
    # Input: math question
    # Expected: calls calculator tool, returns correct answer
    
def test_end_turn():
    """Verify end_turn extraction."""
    # Input: simple greeting
    # Expected: no tools called, direct response
    
def test_multiple_loops():
    """Verify multi-turn tool use."""
    # Input: "Calculate 10 + 5, then multiply by 2"
    # Expected: calls calculator twice in sequence
```

**CCA-F concepts tested:**
- `stop_reason` handling (tool_use vs. end_turn)
- Tool result injection with `role="user"`
- Loop continuation logic
- Multiple tool calls in sequence

### Exercise 2: Design an MCP Tool Schema (60 minutes)

**Goal:** Design a tool that Claude can use reliably.

**Scenario:** You're building a GitHub integration. Design a tool for creating issues.

**Anti-pattern:**
```json
{
  "name": "create_issue",
  "description": "Create a GitHub issue",
  "input_schema": {
    "type": "object",
    "properties": {
      "input": {"type": "string"}
    }
  }
}
```

**Better design:**
```json
{
  "name": "github_create_issue",
  "description": "Create a new GitHub issue in the specified repository. Returns the issue number and URL.",
  "input_schema": {
    "type": "object",
    "properties": {
      "owner": {
        "type": "string",
        "description": "GitHub repository owner (e.g., 'anthropics')"
      },
      "repo": {
        "type": "string",
        "description": "Repository name (e.g., 'anthropic-sdk-python')"
      },
      "title": {
        "type": "string",
        "description": "Issue title (max 255 chars)"
      },
      "body": {
        "type": "string",
        "description": "Issue description (supports Markdown)"
      },
      "labels": {
        "type": "array",
        "items": {"type": "string"},
        "description": "Labels to apply (e.g., ['bug', 'priority-high'])",
        "default": []
      },
      "assignee": {
        "type": "string",
        "description": "GitHub username to assign (optional)",
        "default": null
      }
    },
    "required": ["owner", "repo", "title", "body"]
  }
}
```

**Why is this better?**
1. ✅ Name is specific (`github_create_issue`, not generic `create_issue`)
2. ✅ Description tells Claude WHAT it returns (issue number + URL)
3. ✅ Each property has a clear description + examples
4. ✅ Enums used where appropriate (labels = predefined set)
5. ✅ Required vs. optional is explicit
6. ✅ Defaults provided for optional fields

**Test this tool:**
```python
# Can Claude reliably pick this tool when you ask:
# "Create a GitHub issue in anthropics/anthropic-sdk-python about a bug"
# Answer: Yes (tool name, owner/repo in description, title matches "bug")

# Can Claude avoid picking this tool when you ask:
# "Show me my GitHub issues"
# Answer: Yes (tool is for CREATE, not READ)
```

### Exercise 3: Set Up Claude Code in a Real Project (120 minutes)

**Goal:** Create a CLAUDE.md, custom commands, and hooks for an existing project.

**Steps:**

1. **Create root CLAUDE.md:**
```markdown
# My Project CLAUDE.md

## Build Commands
- `npm run dev`: Start dev server
- `npm test`: Run tests
- `npm run build`: Production build

## Project Structure
- src/components: React components
- src/api: API routes
- src/lib: Utilities

## Key Constraints
- Always run tests before committing
- Don't modify schema without migration
- All API responses use `{ data: ... }` wrapper

## When to Use Which Command
- `/test`: Run test suite
- `/fix-test`: Debug failing test
- `/format`: Run prettier
```

2. **Create custom command `.claude/commands/test.md`:**
```yaml
---
description: "Run tests and report failures"
allowed-tools:
  - bash
  - read
model: claude-3-5-haiku
---

# Run Tests

Run `npm test`. If tests fail, analyze the output and suggest fixes.
```

3. **Create hook `.claude/rules.json`:**
```json
{
  "hooks": {
    "PreWriteFile": [
      {
        "condition": "filename matches *.sql",
        "action": ".claude/hooks/guard-sql.sh"
      }
    ]
  }
}
```

4. **Test it:**
```bash
$ claude /test
$ claude /format
```

**CCA-F concepts tested:**
- CLAUDE.md hierarchy
- Custom command design
- Hook setup

### Exercise 4: Build a Structured Extraction Pipeline (120 minutes)

**Goal:** Extract structured JSON reliably using tool_choice.

**Scenario:** Extract customer contact info from unstructured email text.

```python
import json
from anthropic import Anthropic

client = Anthropic()

def extract_contacts(email_text: str) -> dict:
    """Extract structured contacts from email."""
    
    system = "Extract customer contact information."
    
    tools = [
        {
            "name": "save_contacts",
            "description": "Save extracted customer contact information",
            "input_schema": {
                "type": "object",
                "properties": {
                    "contacts": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "email": {"type": "string", "format": "email"},
                                "phone": {"type": "string"},
                                "company": {"type": "string"}
                            },
                            "required": ["name", "email"]
                        }
                    }
                },
                "required": ["contacts"]
            }
        }
    ]
    
    messages = [{"role": "user", "content": email_text}]
    
    # Force the tool
    response = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=1024,
        system=system,
        tools=tools,
        tool_choice={"type": "tool", "name": "save_contacts"},
        messages=messages
    )
    
    # Extract and validate
    tool_use = response.content[0]
    contacts = tool_use.input["contacts"]
    
    # Validation
    for contact in contacts:
        assert "name" in contact, "Missing name"
        assert "email" in contact, "Missing email"
    
    return {"contacts": contacts}

# Test
if __name__ == "__main__":
    email = """
    Hi, I'm John Doe from Acme Corp. My email is john@acme.com.
    Also, please reach out to Sarah Smith at sarah.smith@acme.com.
    """
    
    result = extract_contacts(email)
    print(json.dumps(result, indent=2))
```

**CCA-F concepts tested:**
- `tool_choice` enforcement
- Schema-based validation
- Error handling (missing fields)
- Retry-with-feedback pattern

---

## 📋 PART 4: Exam Strategy & Heuristics

### How to Approach Exam Questions

**Template for every scenario question:**

1. **Understand the problem:** Who is the user? What can go wrong?
2. **Identify the architecture:** Agentic, single-tool, streaming, cached?
3. **List constraints:** Token budget? Latency? Safety requirements?
4. **Evaluate each answer option:**
   - Does it solve the problem?
   - Does it follow CCA-F patterns?
   - What's the trap (what's tempting but wrong)?
5. **Pick the answer:** Choose the option that best aligns with CCA-F principles.

### Common Exam Traps (By Domain)

#### D1 Traps
- ❌ "Orchestrator should have all subagent tools" → Context explosion
- ❌ "Retry without feedback" → Claude repeats same mistake
- ✅ "Each subagent has isolated context and specific tools"

#### D2 Traps
- ❌ "20 tools is fine if descriptions are good" → Still overloads Claude
- ❌ "Hardcode credentials in .mcp.json" → Security leak
- ✅ "4–5 tools per server; use environment variables for secrets"

#### D3 Traps
- ❌ "CLAUDE.md can prevent security issues" → It's guidance, not enforcement
- ❌ "Use --allowedTools=* in CI for full access" → Dangerous
- ✅ "Use hooks (PreWriteFile) + minimal --allowedTools for CI"

#### D4 Traps
- ❌ "Few-shot examples always help" → Add cost; only use if needed
- ❌ "System prompt can force JSON output reliably" → No, use tool_choice
- ✅ "Tool_choice enforcement > prompt guidance"

#### D5 Traps
- ❌ "Context caching is always enabled" → Opt-in; must set breakpoints
- ❌ "Prompt caching has no cost" → Writing to cache costs 10% extra
- ✅ "Cache system + tools, not conversation history"

### Time Management (120 minutes for 60 questions)

```
Budget: 2 minutes per question average

Actual allocation:
- Easy questions (30%): 1.5 min each
- Moderate questions (50%): 2 min each
- Hard questions (20%): 3 min each

Do not get stuck. Guess and move on. You can come back if time permits.
```

---

## 🔧 PART 5: Prompt Template for Claude Code Execution

Save this as `.claude/commands/cca-quiz.md` to run exam prep in Claude Code:

```yaml
---
description: "Run a domain-specific CCA-F quiz"
allowed-tools:
  - read
model: claude-3-5-sonnet-20241022
---

# CCA-F Quiz: Domain $ARGUMENTS

Run a 5-question quiz on the specified domain. For each question:
1. Present the question
2. Wait for my answer (A/B/C/D)
3. Tell me if I'm right and explain why
4. Name the trap in the wrong answers

Domains:
1 = Agentic Systems
2 = Tools & MCP
3 = Claude Code
4 = Prompt Engineering
5 = Context & Memory

Example usage: /cca:quiz 1
```

---

## ✅ PART 6: Final Exam Checklist (Night Before)

- [ ] Reviewed all 5 domain overviews (30 min)
- [ ] Ran 1 full mock exam (120 min)
- [ ] Scored 850+ on mock (if not, study more)
- [ ] Know the domain weights (27%, 18%, 20%, 20%, 15%)
- [ ] Know the 6 scenarios
- [ ] Know which scenarios have practice coverage (only 4)
- [ ] Built at least 2 hands-on exercises (agent loop, MCP tool)
- [ ] Understand the "why" behind each CCA-F principle, not just the "what"

---

## 🎯 Reference: Exam Simulation Tips

**The day before:**
- Don't cram new material
- Review your weak spots (from mock exam)
- Sleep 8 hours
- Eat a good breakfast before the exam

**During the exam:**
- Read each question twice
- Don't second-guess your first answer
- If stuck, make an educated guess and move on
- Manage time: 2 min per question avg

**If you finish early:**
- Review flagged questions
- Check your reasoning against CCA-F principles
- Don't change answers unless you're certain

---

## 🚀 Recommended 4-Week Study Plan

**Week 1: Foundations (20 hours)**
- D1: Agentic Architecture (6 hours)
  - Read all D1 material
  - Build Exercise 1 (agentic loop)
  - Quiz yourself on agentic patterns
- D2: Tool Design (4 hours)
  - Read all D2 material
  - Build Exercise 2 (MCP tool design)
- D3 + D4: Claude Code + Prompt Engineering (6 hours)
  - Read D3 and D4 material
  - Build Exercise 3 (CLAUDE.md setup)
- D5: Context Management (4 hours)
  - Read D5 material

**Week 2: Deep Dives (20 hours)**
- Take 1 domain deep-dive per day
- Spend 2–3 hours per domain (re-read, quiz, build)
- Build Exercise 4 (structured extraction)
- Review weak spots

**Week 3: Practice & Integration (20 hours)**
- Run 3 mock exams (3 × 120 min = 6 hours)
- Review all wrong answers (6 hours)
- Re-read weak domain sections (4 hours)
- Do hands-on exercises again (4 hours)

**Week 4: Final Review (10 hours)**
- 1 final full mock exam (2 hours)
- Review weak spots (2 hours)
- Night before: light review, sleep 8 hours
- Day of: arrive 15 min early, breathe

---

## 📚 Official Resources (Verified Links)

- **Exam Registration:** anthropic.skilljar.com/claude-certified-architect-foundations-access-request
- **Official Study Guide:** See your Skilljar dashboard
- **Practice Exam:** Available through Skilljar (covers 4 scenarios)
- **Claude API Docs:** docs.anthropic.com
- **Claude Code Docs:** claudeai.io/code

---

## 🏁 Final Note

The CCA-F is **not about memorization**. It's about architectural judgment.

The difference between a passing architect and a failing one:
- **Passing:** Thinks about scale, failure modes, token budgets, and trade-offs
- **Failing:** Treats it like a trivia quiz

Build real systems. Make real mistakes. Learn from them. That's what the exam tests.

**Good luck.**

---

**Version History:**
- 2.0 (June 2026): Fixed domain weights, updated Claude Code syntax, added latest exam intel
- 1.0 (Feb 2026): Initial version

**Last Updated:** June 2, 2026
**Status:** Production Ready for Claude Code CLI


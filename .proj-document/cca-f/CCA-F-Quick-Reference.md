# ⚡ CCA-F Quick Reference Cheat Sheet
**For exam day or last-minute review**

---

## 🎯 Domain Weights (Memorize These)
```
D1: Agentic Systems           27% (16 questions)
D2: Tools & MCP               18% (11 questions)
D3: Claude Code Config        20% (12 questions)
D4: Prompt Engineering        20% (12 questions)
D5: Context & Reliability     15% (9 questions)
```

---

## 🔴 CRITICAL D1 PATTERNS (Agentic Systems)

### The Stop Reason Loop
```python
while True:
    response = client.messages.create(..., messages=messages)
    
    if response.stop_reason == "tool_use":
        messages.append({"role": "assistant", "content": response.content})
        for tool_use in response.content:
            messages.append({
                "role": "user",  # ← NOT "tool" or "assistant"
                "content": [{"type": "tool_result", "tool_use_id": ..., "content": ...}]
            })
    elif response.stop_reason == "end_turn":
        return extract_text(response.content)
```

**Trap:** Using `role="tool"` for tool results → FAILS  
**Right:** Using `role="user"` for tool results → CORRECT

### Hub-and-Spoke Pattern
```
Orchestrator (plans, delegates, aggregates)
  ├─ Researcher Agent (has search tools)
  ├─ Analyst Agent (has compute tools)
  └─ Writer Agent (has format tools)

❌ DON'T: Give orchestrator all tools
✅ DO: Delegate via messages, not tool exposure
```

### Parallel vs Serial Tool Calls
```
❌ Call Tool A → wait → Call Tool B (serial = slow)
✅ Call Tool A + B simultaneously (parallel = fast)

How to prompt for parallel:
"You have tools X, Y, Z. Call them all in this response to solve quickly."
```

---

## 🟠 CRITICAL D2 PATTERNS (Tools & MCP)

### Tool Schema Golden Rules
```json
{
  "name": "verb_object",                    // ✅ search_documents, NOT "doSearch"
  "description": "Clear for MODEL, not human. Explain WHAT it returns.",
  "input_schema": {
    "type": "object",
    "properties": {
      "field": {
        "type": "string|number|array",     // ✅ Strict types, NOT "anything"
        "enum": ["A", "B", "C"],           // ✅ Enums > free text
        "description": "Clear intent + example"
      }
    },
    "required": ["field"]                   // ✅ Explicit required
  }
}
```

**Trap:** Vague descriptions → Claude can't use tool reliably  
**Right:** Specific name + "returns X" in description

### Tool_Choice Values
```
"auto"          → Claude MAY or MAY NOT use tools (default)
"any"           → Claude MUST use at least one tool
{"type": "tool", "name": "X"}  → Claude MUST use tool X only

⚡ For extraction: ALWAYS use tool_choice={"type": "tool", "name": "..."}
   NOT tool_choice="any" or just system prompt instructions
```

### MCP Config Scoping
```
Priority order:
1. .mcp.json (project-specific, highest)
2. ~/.claude.json (user global)

Credentials:
❌ "token": "ghp_abc..."           (hardcoded = security breach)
✅ "token": "${GH_TOKEN}"          (env var = secure)
```

### Tool Count Limit
```
Recommended: 4–5 tools per MCP server
Limit: ~10–15 before Claude's routing degrades

If >10 tools needed: Split into multiple MCP servers
```

---

## 🟡 CRITICAL D3 PATTERNS (Claude Code)

### CLAUDE.md Precedence (Highest Priority First)
```
1. .claude/commands/{cmd}.md (frontmatter overrides)
2. {module}/.claude/CLAUDE.md (module-specific)
3. ./CLAUDE.md (project root)
4. ~/.claude/CLAUDE.md (user global)

Example: If root says "run tests" but module says "run linter first"
→ Module rule wins (highest priority in that scope)
```

### Custom Commands
```markdown
---
description: "What this command does"
allowed-tools: [read, write, bash]  # ✅ Whitelist, not wildcard
model: claude-3-5-haiku             # Optional: override model
---

# Command Name

Your prompt here. Use $ARGUMENTS for dynamic input.
```

**Invoke:** `claude /command:name arg1 arg2`

### Hooks (Safety Gates)
```yaml
# .claude/rules.json or .claude/hooks/ subdirectory
Hook events:
- PreWriteFile: Before Write tool (catch rm -rf patterns)
- PostToolUse: After tool result
- PreToolCall: Before ANY tool

Example:
if [[ "$command" =~ ^rm\ -rf ]]; then
  echo "BLOCKED"
  exit 1
fi
```

### Claude Code in CI/CD
```bash
# Headless mode with permissions
claude -p --allowedTools=read,grep,bash << 'EOF'
Review these files: ${{ github.event.files }}
EOF
```

**Key:** `--allowedTools` whitelist (default denies everything)

---

## 🟢 CRITICAL D4 PATTERNS (Prompt Engineering)

### Force JSON Output with Tool_Choice
```python
# ❌ WRONG: Hoping Claude returns valid JSON
response = client.messages.create(..., system="Return JSON only")
json.loads(response.content[0].text)  # Fails 5% of the time

# ✅ RIGHT: Force a tool that requires valid JSON input
response = client.messages.create(
    tools=[{"name": "save_data", "input_schema": {...}}],
    tool_choice={"type": "tool", "name": "save_data"},
    messages=[...]
)
# Tool input MUST match schema → Always valid JSON
data = json.loads(response.content[0].input)
```

**Key insight:** tool_choice enforcement > prompt instructions

### When to Use Few-Shot Examples
```
✅ USE few-shot when:
  - Task is ambiguous (edge cases need clarification)
  - Example prevents common mistakes
  - You measured improvement

❌ SKIP few-shot when:
  - Task is straightforward (translation, summarization)
  - It's just adding tokens (cost + latency)
```

### Validation Retry (Not Blind Retry)
```python
for attempt in range(3):
    response = extract(prompt)
    try:
        validate(response)
        return response
    except ValidationError as e:
        if attempt < 2:
            prompt += f"\nError: {e}. Fix and retry."  # Feedback
        else:
            raise
```

**Key:** Tell Claude WHAT broke, so it can fix it

---

## 🔵 CRITICAL D5 PATTERNS (Context & Memory)

### CALM Framework
```
C - Capacity: How many tokens available?
A - Architecture: How structure the context?
L - Lifecycle: When to summarize/trim?
M - Metrics: How measure quality?

Strategies:
1. Sliding window: Keep recent N messages
2. Hierarchical: Facts block + conversation block
3. Summarization: Periodically replace old turns with summary
```

### Prompt Caching Basics
```
Cached (system + tools): Stable content that repeats across requests
Not cached (conversation): Request-specific, changes every time

Cost math:
- Write to cache: +10% cost
- Read from cache: 10% of normal cost
- Example: 2 requests = 1.1x + 0.1x = 1.2x total (vs 2.0x without cache)

Max 5 cache breakpoints per message
```

### Token Budgeting
```
Total window: 200K
System: 500 tokens
Tools: 300 tokens
Reserved for response: 4K tokens (safety)

Available for history: ~195K tokens
```

### Context Window Sizes (Know These)
```
Claude 3.5 Sonnet: 200K input, 4K output
Claude 3.5 Haiku: 200K input, 1K output
(Check docs for latest)
```

---

## 🚨 EXAM TRAPS BY DOMAIN

### D1 Traps
- ❌ "Retry tool calls without feedback"
- ❌ "Orchestrator should have all subagent tools"
- ✅ RIGHT: Orchestrator delegates via messages, subagents isolated

### D2 Traps
- ❌ "20 tools is fine if descriptions are good"
- ❌ "Hardcode API keys in .mcp.json"
- ✅ RIGHT: 4–5 tools per server, use ${ENV_VAR}

### D3 Traps
- ❌ "Natural language CLAUDE.md prevents security issues"
- ❌ "Use --allowedTools=* in CI for convenience"
- ✅ RIGHT: Hooks enforce constraints, minimal whitelist in CI

### D4 Traps
- ❌ "Few-shot examples always help"
- ❌ "System prompt can force JSON reliably"
- ✅ RIGHT: tool_choice > system prompt, examples only if needed

### D5 Traps
- ❌ "Caching is always on"
- ❌ "Context can grow unbounded"
- ✅ RIGHT: Cache opt-in with breakpoints, implement strategy

---

## ⏱️ TIME MANAGEMENT FORMULA

60 questions × 2 minutes = 120 minutes

Allocation:
- Easy (30%): 18 questions × 1.5 min = 27 min
- Moderate (50%): 30 questions × 2 min = 60 min
- Hard (20%): 12 questions × 3 min = 36 min
- Buffer: 0 min (tight! don't overthink)

**If stuck > 2 min:** Guess and move on. Review at end if time.

---

## 🧠 THINK LIKE AN ARCHITECT

**For every exam question, ask:**

1. **Who is the user?** Developer, end-user, internal system?
2. **What can go wrong?** Hallucinations, injection, latency, cost?
3. **What's the scale?** 10 requests/day vs 10K?
4. **Is an agent needed?** Or is one API call enough?
5. **What breaks first?** Token budget, latency, safety?

**Answer:** Pick the option that handles the biggest risk.

---

## 🎯 LAST-MINUTE CHECKLIST (Hour Before Exam)

- [ ] Know domain weights by heart (27%, 18%, 20%, 20%, 15%)
- [ ] Know the 6 scenarios
- [ ] Know stop_reason values (tool_use, end_turn)
- [ ] Know tool_choice values (auto, any, {type: tool, name: X})
- [ ] Know CLAUDE.md precedence order (cmd > module > project > user)
- [ ] Know MCP config scoping (.mcp.json > ~/.claude.json)
- [ ] Know tool count limit (4–5 per server)
- [ ] Know prompt caching cost (write 10%, read 10%)
- [ ] Know passing score (720/1000)
- [ ] Know question format (60 questions, 4 per scenario, 120 min)

---

## 💪 FINAL WORDS

**The exam tests systems thinking, not trivia.**

You don't need to memorize every detail. You need to understand:
- Why each pattern matters
- What breaks without it
- How to recognize the trap in wrong answers

You've built production Claude systems. Trust that judgment.

**You've got this. Go get that certification.** 🚀

---

**Cheat Sheet Version:** 2.0  
**Print this page if taking exam on paper**  
**Last Updated:** June 2, 2026

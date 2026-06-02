# CCA-F Study Guide — Build Instructions

## Purpose

You are being asked to generate a **single-file HTML study guide** deployable to GitHub Pages.
This guide is a step-by-step blueprint and execution plan for passing the **Anthropic Certified Claude Associate — Foundations (CCA-F)** exam.

The author is a Senior Java Architect/Developer with real production experience building multi-service AI systems (Spring Boot + nginx + React + MySQL), including JWT auth, streaming SSE pipelines, agentic tool design, and Docker-based infrastructure. Frame all content at that level — skip basic definitions, focus on **architectural trade-offs, gotchas, and production-grade reasoning**.

---

## Output Spec

- **Single file:** `index.html`
- **Deploy target:** GitHub Pages (no build step, no npm, pure HTML/CSS/JS)
- **Offline-capable:** all styles and scripts either inline or from a reliable CDN (e.g., `cdnjs.cloudflare.com`)
- **Dark/light mode toggle** preferred
- **Collapsible sections** per scenario for focused study
- **Progress tracker** (checkboxes per section, persisted in `localStorage`)

---

## Reference Project

This project (`secure-openrouter-docker`) is the hands-on reference implementation.
It demonstrates many CCA-F exam patterns in production code. Use it throughout the guide
to ground abstract concepts in concrete, real examples.

**Architecture:**
```
Client (React) → Spring Boot (JWT, rate limiting, SSE) → nginx proxy → OpenRouter (free LLMs) → MySQL
```

**Key patterns already implemented here:**
- Multi-provider abstraction (OpenRouterClient, model allowlist)
- Streaming with SSE (`SseEmitter`, token-by-token delivery)
- Agentic tool design (BYOK key management, model toggle endpoints)
- Structured data extraction patterns (chat log storage, conversation threading)
- Rate limiting (Bucket4j per-user), JWT auth, AES-GCM encryption
- CI/CD readiness (Dockerfiles, health checks, Flyway migrations)
- Admin + user role separation, granular model gating

---

## Exam Scenario Pool (What the Author Knows)

The official exam guide lists **6 scenarios**, but the real exam pool contains **at least 13**.
The practice exam only covers **4**. The real exam may include scenarios not in the guide.

### Official Guide Scenarios (6)
1. Customer Support Resolution Agent
2. Code Generation with Claude Code
3. Multi-Agent Research System
4. Developer Productivity with Claude
5. Claude Code for Continuous Integration
6. Structured Data Extraction

### Extended Pool (7 additional — confirmed via community reports)
7. Agentic Tool Design
8. Long Document Processing
9. Claude for Operations
10. Conversational AI Patterns
11. Agent Skills for Enterprise Knowledge Management
12. Agent Skills for Developer Tooling
13. Agent Skills with Code Execution

### Practice Exam Coverage
Only 4 of the above have practice questions: #1, #2, #3, #5.
Do not let a high practice score create false confidence — 9+ scenarios have no practice coverage.

---

## Guide Structure

Build the guide as a tabbed or sectioned single-page dashboard. Each major section below
should be a collapsible card or tab with a completion checkbox.

---

### Section 0 — Exam Meta (read first)

- Exam format: scenario-based, 4 scenarios randomly selected from the pool
- Each scenario: ~10 questions testing architectural judgment, not trivia
- Passing strategy: think like an engineer designing the system, not like someone memorizing facts
- What actually prepares you: **building things** — not grinding practice questions
- Third-party practice questions are of limited value; official materials + hands-on are the signal

---

### Section 1 — Core Foundations (applies to every scenario)

Cover these regardless of which scenarios appear:

#### 1.1 Claude API Fundamentals
- Messages API structure (system, user, assistant turns)
- Streaming vs. non-streaming: when each is appropriate
- Token counting, context window limits, cost/latency trade-offs
- Stop sequences, temperature, top_p — practical impact, not just definitions
- Tool use / function calling: schema design, result injection

#### 1.2 Prompt Engineering for Production
- System prompt design: role, constraints, output format
- Few-shot examples: when they help vs. when they add noise
- Chain-of-thought: when to request it explicitly
- Prompt injection risks and mitigations
- Testing prompts: regression suites, eval frameworks

#### 1.3 Claude Code
- What it is: agentic CLI tool, not just autocomplete
- Slash commands, hooks, MCP server integration
- CLAUDE.md as project memory — what belongs there
- When to use Claude Code vs. API directly vs. SDK
- CI integration: safe use in automated pipelines (permissions, sandboxing)

#### 1.4 MCP (Model Context Protocol)
- Server vs. client roles
- Tool schema design: name, description, inputSchema — precision matters
- Resource exposure vs. tool invocation — when each is right
- Security surface: what an MCP server can access, principle of least privilege
- Real example from this project: model toggle endpoint as an MCP-style tool

#### 1.5 Agentic Patterns
- ReAct loop (Reason → Act → Observe → Repeat)
- Tool use chains: sequential vs. parallel
- Error handling in agents: retry logic, fallback strategies, human escalation
- State management across turns
- When NOT to use an agent (simpler is better)

#### 1.6 Safety and Reliability
- Constitutional AI concepts (high-level, not implementation detail)
- Guardrails: input validation, output validation, content filtering
- Rate limiting strategies (per-user, per-model, per-day) — reference Bucket4j impl in this project
- Audit logging — reference chat_logs table in this project
- Graceful degradation when upstream fails

---

### Section 2 — Scenario Deep-Dives

For each scenario: **What the system does → Key design decisions → Trade-offs → Gotchas → Project tie-in**

#### 2.1 Customer Support Resolution Agent
- Intent classification → routing → response generation → escalation
- Multi-turn memory: conversation threading (reference `conversations` + `conversation_messages` tables)
- When to escalate to human: confidence thresholds, sentiment detection
- Tool use: CRM lookup, ticket creation, knowledge base search
- Evaluation: resolution rate, CSAT, escalation rate
- **Gotcha:** don't conflate "resolved" with "response sent" — measure outcomes

#### 2.2 Code Generation with Claude Code
- Agentic loop for code tasks: plan → implement → test → iterate
- CLAUDE.md as the "contract" between developer and agent (reference this project's CLAUDE.md)
- Safe permissions model: what the agent can read/write/execute
- Diff review before apply — never blindly accept
- CI integration: linting, testing gates before merge
- **Gotcha:** agents accumulate context — long sessions drift; fresh context for fresh tasks

#### 2.3 Multi-Agent Research System
- Orchestrator + specialist subagents pattern
- Parallelism: fan-out searches, aggregate results
- Deduplication and source reconciliation
- Citation integrity: agents hallucinate citations — always verify
- Trust boundaries between agents: treat subagent output as untrusted input
- **Gotcha:** latency compounds in serial chains — design for parallelism

#### 2.4 Agentic Tool Design
- Tool naming: verb-object convention (`search_documents`, not `doSearch`)
- Description quality: the model reads this, not a human — be precise and unambiguous
- Input schema: strict types, enums over free strings where possible
- Idempotency: tools that mutate state should be safe to retry
- Error responses: structured, not generic — agent needs actionable feedback
- **Project tie-in:** model toggle endpoint (`PUT /api/user/models/{id}/toggle`) uses integer PK
  to avoid URL-encoding issues with slash-containing model IDs — this is real agentic tool design

#### 2.5 Developer Productivity with Claude
- Code review automation: what Claude can catch vs. what requires human judgment
- Codebase Q&A: retrieval-augmented generation over local files
- Documentation generation from code
- IDE integration vs. CLI vs. API — choosing the right surface
- **Gotcha:** stale context — Claude's knowledge of your codebase is only as fresh as what you give it

#### 2.6 Long Document Processing
- Chunking strategies: fixed-size vs. semantic vs. hierarchical
- Map-reduce pattern: process chunks → summarize → synthesize
- Preserving cross-chunk context (entities, references)
- When to use retrieval vs. stuffing the full document in context
- Output formats: structured extraction vs. narrative summary
- **Gotcha:** Claude reads the whole context window — long docs push out earlier conversation

#### 2.7 Claude for Operations
- Runbook automation: Claude as on-call assistant
- Alert triage: classify severity, suggest remediation, escalate
- Tool use: query metrics, read logs, trigger rollbacks
- Safety gates: require human confirmation for destructive ops
- **Gotcha:** ops agents need strict output validation — a hallucinated kubectl command is dangerous

#### 2.8 Claude Code for Continuous Integration
- Non-interactive mode: `claude --print`, structured output
- Permissions in CI: read-only by default, explicit write grants
- Timeout and token budget management in pipelines
- Integration patterns: GitHub Actions, Jenkins, pre-commit hooks
- When to fail the pipeline vs. warn and continue
- **Project tie-in:** this project's Phase 5 roadmap item (GitHub Actions CI/CD)

#### 2.9 Structured Data Extraction
- Output schema design: JSON Schema, TypeScript interfaces
- Validation: always parse and validate model output — never trust raw strings
- Handling ambiguity: when the source is unclear, return null vs. ask for clarification
- Confidence scores: useful for downstream routing
- Batch extraction: parallelism, error isolation per record
- **Project tie-in:** chat_logs extraction pipeline, Flyway migration schema as extraction target contract

#### 2.10 Conversational AI Patterns
- Session management: stateless API + external session store
- Context window budget: prioritize recent + system over old turns
- Persona consistency across long conversations
- Interruption handling: user changes topic mid-task
- **Gotcha:** don't let conversation history grow unbounded — implement sliding window or summarization

#### 2.11 Agent Skills for Enterprise Knowledge Management
- RAG pipeline: embed → store → retrieve → generate
- Access control: retrieved context must respect source permissions
- Freshness: embeddings go stale — index update strategy
- Multi-source fusion: SharePoint + Confluence + Slack in one query
- **Gotcha:** retrieval quality determines answer quality — garbage in, garbage out

#### 2.12 Agent Skills for Developer Tooling
- MCP server wrapping internal APIs (JIRA, GitHub, CI systems)
- Tool composition: agent chains multiple tools per task
- Authentication delegation: OAuth tokens, not hardcoded secrets
- **Project tie-in:** Jira MCP connector available in this environment — study its schema as a real example

#### 2.13 Agent Skills with Code Execution
- Sandboxing: never execute untrusted code in the host environment
- Output capture and parsing: stdout/stderr as tool results
- Security surface: code injection, resource exhaustion, filesystem access
- Allowed languages/runtimes: declare explicitly, reject others
- **Gotcha:** agents will attempt to install packages — constrain pip/npm in the sandbox

---

### Section 3 — Hands-On Checklist

These exercises build the judgment the exam tests. Check each off as you complete it.

- [ ] Build a working ReAct agent with at least 2 tools (search + write)
- [ ] Design an MCP server with 3+ tools; test tool descriptions by asking Claude to pick the right one
- [ ] Set up Claude Code in a real project with a CLAUDE.md; run a multi-step refactor
- [ ] Wire Claude Code into a CI pipeline (GitHub Actions or local pre-commit hook)
- [ ] Build a structured extraction pipeline: source doc → JSON schema → validated output
- [ ] Implement SSE streaming end-to-end (reference this project's `ConversationController`)
- [ ] Design a rate-limiting strategy for a multi-user LLM system (reference Bucket4j impl here)
- [ ] Write a system prompt that resists prompt injection; test it adversarially
- [ ] Build a multi-agent system: orchestrator delegates to 2 parallel subagents, aggregates
- [ ] Implement an audit log for all LLM calls (reference `chat_logs` table in this project)

---

### Section 4 — Judgment Heuristics (for unexpected scenarios)

When you see a scenario you haven't studied, apply these:

1. **Who is the user?** End user, developer, internal ops, or another agent?
2. **What can go wrong?** Hallucination, injection, data leakage, runaway costs, latency?
3. **What's the blast radius?** Read-only vs. mutating. Reversible vs. irreversible.
4. **Is an agent needed?** Or is a single well-prompted API call sufficient?
5. **How do you evaluate success?** Define the metric before designing the system.
6. **What breaks at scale?** Context window, latency, cost, rate limits, consistency.

---

### Section 5 — Key ADRs from This Project (Exam-Relevant Decisions)

These are real architectural decisions made during development of this project.
Each represents the kind of trade-off reasoning the exam tests.

| Decision | Why it matters |
|---|---|
| Flyway instead of `ddl-auto=update` | Schema is production data — migrations must be auditable and repeatable |
| Integer PK in tool path, not string modelId | Model IDs contain `/` — URL encoding breaks MVC routing; design tools around transport constraints |
| SSE tokens must be JSON-encoded | Raw `\n` in SSE `data:` field is treated as event separator — protocol gotcha |
| AES-GCM for stored API keys, not BCrypt | Keys must be decrypted (retrievable), not just verified — wrong hash function choice breaks functionality |
| Bucket4j per-user rate limiting | Global limits punish well-behaved users; per-user isolation is fairer and safer |
| Request limit pre-call, token limit post-call | Tokens unknown before call completes — two-phase limit enforcement is the only correct model |
| nginx Authorization pass-through | Injecting a system key into every request hides per-user attribution — BYOK requires forwarding the user's key |
| Sparse preference table (absence = enabled) | Pre-populating every user×model combination doesn't scale; default-enabled is the right sparse-table pattern |
| `@Transactional` on controller methods accessing lazy collections | Hibernate session lifecycle — missing this causes `LazyInitializationException` in production |
| ASYNC dispatcher must `permitAll()` in SecurityConfig | Spring Security intercepts the async re-dispatch with no SecurityContext — SSE-specific gotcha |

---

## Final Note to the Generating Agent

Do not produce a generic study guide. Every section should reflect the engineering judgment
of someone who has actually built production AI systems. Where the reference project
(`secure-openrouter-docker`) provides a concrete example, cite the specific file, class,
endpoint, or ADR. The exam rewards architectural thinking — the guide should train that,
not memorization.

Make the HTML beautiful, navigable, and practical. The author will read this the night
before the exam and during final review. It should be dense with signal and zero fluff.

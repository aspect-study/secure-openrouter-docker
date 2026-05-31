# AspectOR — Claude Session Workflow Guide

A personal reference for working efficiently with Claude on this project.
Follow this guide every time you start, continue, or end a session.

---

## 1. Starting a New Session

### Open the project folder first
In Claude (Cowork mode), always mount the project folder **before** typing anything:

1. Open Claude desktop app
2. Click the folder icon or use "Select a folder"
3. Navigate to: `C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker`
4. Select it

Claude will automatically detect and read `CLAUDE.md` from the project root.
This gives Claude instant full context — architecture, constraints, commands,
troubleshooting — without you typing any of it.

### First message in a new session
Keep it short. Claude already knows the project from CLAUDE.md:

```
I want to work on [specific feature/bug]. Current services running: [what's up].
```

**Example:**
```
I want to add streaming responses to the Playground. 
Spring Boot and MySQL are running locally, nginx is in Docker.
```

**Do NOT:**
- Paste the entire codebase or long error logs upfront
- Re-explain the architecture ("so we have an nginx proxy that...")
- Ask Claude to "review everything first"

---

## 2. Token Optimization Best Practices

### Keep requests precise
| ❌ Wasteful | ✅ Efficient |
|---|---|
| "Here's my whole PlaygroundPage, can you find the bug?" | "The `sendMessage` function in `PlaygroundPage.tsx` throws on 429 — fix it" |
| "How do I fix this? [500 lines of logs]" | "Spring Boot error: `NullPointerException at ConversationController.java:134`" |
| "Review all my files and suggest improvements" | "Review only the auth flow — `AuthController.java` and `JwtAuthFilter.java`" |
| "Here's what we did last session..." | (Just open the folder — CLAUDE.md covers it) |

### Paste only what's needed
- Logs: `tail -30` or `grep "ERROR"` — not the full output
- Code: paste only the relevant function, not the whole file
- Errors: exact stack trace first line + cause, not the full dump

### Batch related changes
Instead of 5 separate messages, send one:
```
Do three things:
1. Fix the 429 error handling in ConversationController
2. Add a toast in PlaygroundPage when 429 occurs  
3. Restore the input text after failure
```

### Plan before build
For any non-trivial feature, say:
```
Plan this first, don't write code yet: [feature description]
```
Review the plan, confirm or adjust, then say "build it."
This prevents wasted tokens on the wrong implementation.

---

## 3. When to Create a New Session

Create a new session when **any** of these are true:

| Signal | Why |
|---|---|
| Session is 1+ hours old | Context window accumulates overhead |
| You've built 2+ major features | Too much history inflates every message |
| You're switching focus area | e.g., from backend work to UI work |
| Claude starts asking questions it already answered | Context degradation |
| A feature is fully complete and committed | Clean break point |
| You're about to start something completely new | Fresh session = clean thinking |

**Rule of thumb:** Commit to GitHub → end session → start fresh for the next feature.

---

## 4. Saving State Before Ending a Session

Always do this before closing a long session so the next one starts informed.

### Step 1 — Commit your work
```cmd
cd C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker
git add .
git commit -m "feat/fix: [what you built]"
git push origin main
```

### Step 2 — Ask Claude to save memory
Type exactly this (or close to it):

```
Before we end: save the current project state to memory, update CLAUDE.md 
with any new constraints or troubleshooting entries discovered this session, 
and update memory/learnings/README.md with any hard lessons learned.
```

Claude will:
- Write/update `memory/project_aspector_state.md` (what's done, what's next)
- Add new entries to `CLAUDE.md` troubleshooting table
- Add new entries to `memory/learnings/README.md`
- Commit if needed

### Step 3 — Update ADRs if needed
If a non-obvious architectural decision was made this session:
```
Write an ADR for the decision to [X] — store it in memory/adrs/
```

### Step 4 — Update PRD if a phase completed
```
Mark Phase [X] as complete in memory/prds/PRD-001
```

### Step 5 — End the session
Close the Claude window or start a new conversation.

---

## 5. Reopening the Project in a New Session

### The opening ritual (takes 30 seconds)
1. Open Claude desktop
2. Mount folder: `C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker`
3. Type your first message — keep it to 1-2 sentences about what you want today

Claude will read `CLAUDE.md` and be immediately aware of:
- All 4 phases and what's done
- Every constraint (Java versions, port numbers, secret handling)
- All API endpoints
- Every known troubleshooting entry
- The full project structure

### If Claude seems confused about something
Ask it to read a specific file:
```
Read CLAUDE.md and memory/adrs/ to refresh context, then answer: [question]
```

### Start infrastructure before coding
```cmd
# In a CMD window (not the Claude chat)
switch-java-version.bat 21
cd C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker
docker compose up -d openrouter-proxy openrouter-mysql
run-app.bat

# In a separate CMD window
cd admin-ui
npm run dev
```

---

## 6. Quick Reference — Commands

### Start everything locally
```cmd
switch-java-version.bat 21
docker compose up -d openrouter-proxy openrouter-mysql
run-app.bat                    # Spring Boot on :8080
cd admin-ui && npm run dev     # React UI on :3000
```

### Build Spring Boot
```cmd
switch-java-version.bat 21
cd app && gradlew.bat build -x test
```

### Verify stack is healthy
```powershell
docker compose ps                              # nginx + MySQL (healthy)
Invoke-RestMethod http://localhost:8080/actuator/health   # Spring Boot
Invoke-RestMethod http://localhost:8081/health            # nginx proxy
Invoke-RestMethod http://localhost:3000                   # React UI
```

### Test a model via proxy
```powershell
$b = '{"model":"nvidia/nemotron-nano-9b-v2:free","messages":[{"role":"user","content":"hi"}]}'
Invoke-RestMethod -Uri http://localhost:8081/api/v1/chat/completions -Method POST -ContentType application/json -Body $b
```

### Generate a new JWT secret
```powershell
powershell -command "[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))"
```

---

## 7. What's in Each Memory File

| File | Purpose | Update when |
|---|---|---|
| `CLAUDE.md` | Master project doc — structure, commands, constraints, troubleshooting | New endpoints, new constraints, new bugs found |
| `memory/adrs/` | Architectural decisions with context + trade-offs | A non-obvious decision is made or reversed |
| `memory/prds/PRD-001.md` | Product requirements, phase status | Phase completes, scope changes |
| `memory/learnings/README.md` | Hard lessons — bugs that took >30min, gotchas | Any bug that would have been faster to avoid |
| `memory/project_aspector_state.md` | Current state snapshot for session handoff | End of every significant session |

---

## 8. Effective Prompt Patterns

### For bugs
```
[file]:[function] throws [error] when [condition]. 
Log line: [paste exact log line]
Fix it.
```

### For features
```
Plan first (no code): Add [feature] to [component].
Requirements: [bullet list, 3-5 items max]
```

### For reviews
```
Review only [specific file or function] for [security / performance / correctness].
Flag issues only — don't rewrite unless I ask.
```

### For design changes
```
In [component], change [specific element] to [desired result].
Screenshot context: [describe what you see].
```

### For memory saves
```
Save session state to memory. New things learned this session: [1-2 sentences].
Update CLAUDE.md troubleshooting if needed.
```

---

*Last updated: 2026-05-31 — after Flyway migration (ADR-010: schema ownership moved from ddl-auto=update to Flyway V1–V3)*

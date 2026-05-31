# AI Session Context — How Claude Works in This Project

This document explains what Claude does automatically at the start of every session,
what each memory file contains, and how everything connects.

---

## What Loads Automatically (You Do Nothing)

### 1. MEMORY.md — Session Index
Located in Claude's persistent memory store (not in this repo).
Injected into Claude's context at every session start. Contains pointers to:

| Memory File | Type | Purpose |
|---|---|---|
| `project_aspector_state.md` | project | Current phase, running services, constraints, next steps |
| `feedback_keep_memory_updated.md` | feedback | Instructs Claude to update PRDs/ADRs/learnings after architecture-changing sessions |
| `feedback_git_commit_after_task.md` | feedback | Instructs Claude to end every task with a git commit message |
| `reference_github_repo.md` | reference | GitHub repo URL — used to fetch branch/commit state at session start |

### 2. CLAUDE.md — Project Documentation
Checked into this repo. Contains project structure, API endpoints, constraints, ADRs,
and troubleshooting. Loaded automatically because it lives in the project folder.

### 3. GitHub Repo Fetch
Using the saved repo URL (`https://github.com/aspect-study/secure-openrouter-docker.git`),
Claude can check the current branch, recent commits, and push state at session start —
no need to describe git state manually.

---

## What You Do to Start a Session

1. Start Docker, DB, and the Spring Boot app (Claude trusts this is done)
2. Tell Claude the task

That's it.

---

## What Claude Does at the End of Every Task

Provides a `git commit` message for you to run manually:

```
git add .
git commit -m "<suggested message>"
git push
```

Claude does **not** run git commands automatically unless you explicitly say so.

---

## Session Flow Diagram

```
You open a new session
        │
        ▼
┌─────────────────────┐    ┌───────────────────────────┐
│  MEMORY.md loaded   │    │  CLAUDE.md loaded          │
│  (auto-injected)    │    │  (from project folder)     │
└──────┬──────────────┘    └───────────────────────────┘
       │
       ├── project_aspector_state  → where are we, what's next
       ├── feedback: memory        → keep PRDs/ADRs/learnings updated
       ├── feedback: git commit    → give commit msg after every task
       └── reference: github repo → fetch branch + recent commits
                                            │
                                            ▼
                              ┌─────────────────────────┐
                              │  I'm ready — full        │
                              │  context loaded          │
                              └──────────┬──────────────┘
                                         │
                              You: "here's the task"
                                         │
                                         ▼
                              I work → end with commit message
                              You: git add . && git commit -m "..."
```

---

## Project Memory Files (not in repo — per-developer)

Each developer has their own Claude memory store. To bootstrap a new developer's session,
they should run the onboarding prompt below so Claude builds equivalent memory on their end.

---

## Onboarding Prompt for New Team Members

Copy and run this in your first Claude session on this project:

```
I'm a new developer on the AspectOR project (secure-openrouter-docker).
The GitHub repo is: https://github.com/aspect-study/secure-openrouter-docker.git

Please do the following to set up your memory for this project:

1. Fetch the repo and read CLAUDE.md to understand the full project context.
2. Save a project memory entry with: current phase (Phase 4 complete, Phase 5 pending),
   stack (Spring Boot Java 25, nginx proxy, MySQL, React admin UI), and key constraints
   from CLAUDE.md.
3. Save a feedback memory: always end every task with a git commit message.
   I will commit manually unless I say otherwise.
4. Save a reference memory: GitHub repo is https://github.com/aspect-study/secure-openrouter-docker.git
5. After saving all memory, show me a summary of what you saved and confirm you're
   ready to work on this project.

When done, ask me: "What's the current branch and is there anything in progress I should know about?"
```

After running this prompt, share the Claude response (summary of saved memory + readiness confirmation)
back to the team so we can verify your session context matches.

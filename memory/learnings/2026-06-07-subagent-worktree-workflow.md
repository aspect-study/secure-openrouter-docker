# Learnings — 2026-06-07 Session (Subagent-Driven Development + Worktree Workflow)

## 1. Always check for an existing worktree before dispatching subagents

**What happened:** I started Task 1 (PRD-005 domain models) by dispatching a subagent that
created files in the main working directory (`secure-openrouter-docker/app/src/...`).
The user had already created the worktree `feature-gateway-intelligence-agent` with Task 1
already committed on a previous session. The work was duplicated in the wrong location.

**Root cause:** Skipped the `superpowers:using-git-worktrees` check at the start of the
subagent-driven session. The skill is supposed to run before any implementation begins —
it ensures all work happens in the correct isolated workspace.

**Fix:** Before dispatching ANY implementation subagent:
1. Run `git worktree list` to see if a worktree already exists for the feature
2. If yes, target that worktree in all subagent prompts — do NOT create files in main
3. If no, create the worktree first via `superpowers:using-git-worktrees`

**Rule:** The `superpowers:subagent-driven-development` skill always depends on
`superpowers:using-git-worktrees`. Invoke it first, not after the first task is done.

---

## 2. PRD-005 worktree location and branch

**Worktree path:** `C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker\.claude\worktrees\feature-gateway-intelligence-agent`

**Branch:** `worktree-feature-gateway-intelligence-agent`

**Status as of 2026-06-07:** Task 1 (domain models) committed at `80ccad4`.
Null guard fix for `StopReason.fromOpenAiFinishReason(null)` applied but not yet committed.

**All remaining PRD-005 implementation (Tasks 2-10) must target this worktree.**

---

## 3. Running gradlew.bat in the worktree requires explicit JAVA_HOME

**What happened:** PowerShell's default session uses an old Java version (Java 8 was picked up).
`gradlew.bat` in the worktree failed: `Dependency requires at least JVM runtime version 17`.

**Fix:** Always set `$env:JAVA_HOME` before running `gradlew.bat` from PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Or use `run-app.bat` which handles the Java switch automatically.

**Rule:** When running Gradle from PowerShell (not via run-app.bat), always set JAVA_HOME
explicitly. Do not assume the PATH in the PowerShell session is correct.

---

## 4. Subagent prompts for worktree must use the worktree path as working directory

**What happened:** Subagent prompt said `Working directory: C:\...\secure-openrouter-docker`.
The subagent created files in the main working directory, not the worktree.

**Fix:** All subagent implementer prompts must specify:
```
Working directory: C:\Users\ADMIN\IdeaProjects\secure-openrouter-docker\.claude\worktrees\feature-gateway-intelligence-agent
```

And all file paths referenced in the prompt must use this base path, not the repo root.

**Rule:** The working directory in subagent prompts is not documentation — it's the actual
base path the subagent will use for all Read/Write/Edit/Bash operations. Get it wrong and
all work lands in the wrong place.

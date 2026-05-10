# Ralph Loop Workflow

## Core Context

- **Progress Log**: @progress.txt
- **Issue Tracker**: @docs/agents/issue-tracker.md
- **Domain Language**: @CONTEXT.md
- **PRD/Roadmap**: @docs/MVP_0.0.1.md

---

## Pre-Flight (run once at loop start)

Before selecting a task, verify:

- [ ] Git working tree is clean (`git status`)
- [ ] On `main`/`develop` — not a stale feature branch
- [ ] Docker daemon is running (`docker info`)
- [ ] Dependencies are installed (`mvn dependency:resolve` / `npm ci`)

If any check fails → **stop immediately and report the blocker. Do not proceed.**

---

## Escalating Recovery Protocol

Applied whenever anything goes wrong during the loop. Three tiers — work through them in order:

**Tier 1 — Warn**: Log the anomaly to `progress.txt`. If it's transient (flaky test, network hiccup), retry once.

**Tier 2 — Self-fix & retry**: If the root cause is identifiable and safe to fix (missing dependency, stale lock file, wrong env var), fix it, log what was done, and retry the failed step **once**.

**Tier 3 — Stop & notify**: If Tier 2 didn't resolve it, or the fix is outside Ralph's authority (schema change, infra issue, ambiguous requirements): bring the stack down if running, mark the task with the appropriate status, update `progress.txt` with the full blocker description, and **stop to notify the user**.

Rollback rules:

- If a quality gate fails after code was written → `git stash` before stopping.
- If Docker integration fails → `docker-compose down` before stopping.
- Never leave uncommitted broken work or a running stack on exit.

---

## Loop Steps

### 1. Selection

- Read the issue tracker and identify all `ready-for-agent` tasks.
- **Dependency check**: Before selecting, verify that all tasks listed as blockers or prerequisites for each candidate are marked `done`. If a task has unresolved dependencies, skip it (do not change its status) and move to the next candidate.
- Pick the single highest-priority task with resolved dependencies and with triage `ready-for-agent` task. On tie → lowest issue ID wins.
- If the task lacks clear acceptance criteria → mark `needs-info`, skip it, and **stop to ask the user**.
- If no eligible tasks exist → stop and report to user.

### 2. Confidence Assessment

Before writing a single line of code, score your confidence in the task:

| Dimension | Questions to ask |
|---|---|
| **Scope** | Are the acceptance criteria specific enough to write a test? |
| **Dependencies** | Are all APIs, schemas, and interfaces this task relies on already defined? |
| **Risk** | Does this touch shared infrastructure, auth, or data migrations? |

Assign an overall confidence: **High / Medium / Low**.

- **High** → proceed to branching.
- **Medium** → proceed, but note the uncertainty in `progress.txt` and flag it in the commit message.
- **Low** → mark the task `needs-info`, document the specific unknowns, and **stop to ask the user**.

### 3. Branching

- Checkout or create `feature/issue-<id>` from the latest `main`/`develop`.
- Never reuse a branch from a previous failed attempt without a clean reset.

### 4. TDD Execution (Use `/tdd` skill)

- **RED**: Write a failing test that captures the acceptance criteria exactly.
- **GREEN**: Write the minimal code to make it pass — no speculative additions.
- **REFACTOR**: Clean up while staying green. Run the full suite after refactor.
- On failure → apply **Escalating Recovery Protocol**.

### 5. Quality Gates (hard stop on failure)

Use Grep aggressively to *limit* the LOGS.

Use `/code-review` skill to review the code.

**Java**: `mvn -q -B spotless:apply && mvn -q -B verify`

**Frontend**: `npm run lint && npm run build && npm test`

On failure → apply **Escalating Recovery Protocol**.

### 6. Integration & Docker

1. Build: `docker-compose build <service-name>`
2. Start: `docker-compose up -d`
3. Poll health until all services healthy or 10s timeout.
4. Run E2E: `npx playwright test --reporter=list`. When running Playwright, run in quiet mode, such that only test failures are printed to the console.

On failure → apply **Escalating Recovery Protocol** (always bring stack down first).

### 7. Definition of Done

Before committing, every box must be checked:

- [ ] All acceptance criteria from the issue are met (verify each one explicitly)
- [ ] Unit tests pass and cover the acceptance criteria
- [ ] Quality gates pass
- [ ] E2E / integration tests pass on Docker stack
- [ ] No unrelated files changed (`git diff`)
- [ ] `progress.txt` updated: task ID, summary, confidence level, decisions made
- [ ] PRD (`@docs/MVP_0.0.1.md`) updated — mark feature ✅ Done if applicable

### 8. Commit

Follow [Conventional Commits](https://www.conventionalcommits.org/).

Format: `type(scope): short description` — reference issue ID and confidence level in body.

```
feat(auth): add JWT refresh endpoint
Confidence: High
```

update the the Issue to `ready-for-human` using gh and DO NOT CLOSE THE ISSUE   .

### 9. Single Task Policy

One task per iteration. No scope creep. If a related bug or improvement surfaces during work, log it as a new issue — do not fix it now.
Repeat the loop untill all tasks labeled `ready-for-agent` are done.

---

## Completion

If all MVP features in the PRD are marked ✅ Done → output `<promise>COMPLETE</promise>`

Ralph is happy when the circle is closed! 🎡

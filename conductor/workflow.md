# Project Workflow

## Guiding Principles

1. **The Plan is the Source of Truth:** All work must be tracked in `plan.md`. Mid-phase discoveries or scope changes require user approval and an updated plan.
2. **The Tech Stack is Deliberate:** Changes to the tech stack (including new dependencies) must be documented in `tech-stack.md` and approved *before* implementation. Manage all dependencies centrally in the parent `pom.xml`.
3. **Test-Driven Development (TDD):** **CRITICAL: Always write tests first.** Consider edge cases and boundary conditions for every feature. Consult the user if specific test cases could be overlooked.
4. **Formatting & Quality:** **CRITICAL: Always run `mvn spotless:apply` before building or running tests** to ensure code consistency. CI/verify stages must enforce `spotless:check`.
5. **High Code Coverage:** Aim for >80% code coverage for all modules.
6. **User Experience First:** Every decision should prioritize user experience, following the minimalist and analytical guidelines.
7. **High-Signal Output:** **CRITICAL: Always use quiet flags (e.g., `mvn -q`) and batch mode (`-B`)** to minimize noise. Output must be limited to errors, failures, or critical architectural issues to maintain context efficiency.
8. **Git & GitHub Guidelines:** All new features or fixes must be developed on a dedicated feature branch (e.g., `feature/<task-name>` or `fix/<bug-name>`). Do not commit directly to `main`. Open a Pull Request (PR) for all changes, ensure CI checks pass, and use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) (e.g., `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`) for all commit messages.
9. **Agent Directives:** For tasks spanning multiple services or requiring widespread refactoring, prioritize using the `codebase_investigator` sub-agent before proposing or executing code changes.

## Task Workflow

All tasks follow a strict lifecycle:

### Standard Task Workflow

1. **Select Task:** Choose the next available task from `plan.md` in sequential order.

2. **Branching:** If starting a new track or major feature, create and check out a new feature branch (`git checkout -b feature/<name>`).

3. **Mark In Progress:** Before beginning work, edit `plan.md` and change the task from `[ ]` to `[~]`.

4. **Write Failing Tests (Red Phase):**
   - Create a new test file or add to an existing one.
   - **Always write tests before implementation.**
   - **Consider Edge Cases:** Think about null inputs, empty collections, large numbers, and unauthorized access.
   - **Consult User:** If you're unsure about the coverage or edge cases, ask the user for specific scenarios.
   - **CRITICAL:** Apply formatting with `mvn -q spotless:apply`, then run the tests and confirm that they fail as expected.

5. **Implement to Pass Tests (Green Phase):**
   - Write the minimum amount of application code necessary to make the failing tests pass.
   - Apply formatting with `mvn -q spotless:apply`, then run the test suite again and confirm that all tests now pass.

6. **Refactor (Optional but Recommended):**
   - Refactor the implementation and test code for clarity and performance.
   - Apply formatting with `mvn -q spotless:apply`, then rerun tests to ensure they still pass.

7. **Verify Coverage:** Run coverage reports (e.g., `mvn -q jacoco:report`). Target: >80% coverage for new code.

8. **Document Deviations & Bugs:** If implementation differs from the tech stack, or if a critical bug/blocker is discovered, halt execution, ask the user for guidance, and update `plan.md` or `tech-stack.md` before proceeding.

9. **Update Plan (Local):** Mark the task as complete `[x]` in `plan.md`. Since commits are per-phase, do not record a SHA yet.

### Phase Completion Verification and Checkpointing Protocol

**Trigger:** This protocol is executed immediately after all tasks in a phase are marked complete in `plan.md`.

1. **Announce Protocol Start:** Inform the user that the phase is complete and the verification protocol has begun.

2. **Ensure Test Coverage:**
    - Identify all files changed during the phase.
    - Verify that every new or modified code file has corresponding tests.
    - Create any missing tests, following the repository's naming and style conventions.

3. **Execute Automated Tests & Quality Checks:**
    - Announce the test command (e.g., `mvn -q -B spotless:apply test`).
    - Apply formatting with `mvn -q spotless:apply`, then run the tests and address any failures (maximum two fix attempts before stopping for guidance).

4. **Propose Manual Verification Plan:**
    - Generate a step-by-step manual verification plan based on `product.md` and `plan.md`.
    - Wait for explicit user approval before proceeding.

5. **Create Phase Commit (Checkpoint):**
    - Stage all changes (code, tests, and `plan.md`).
    - **Commit Message:** Use a clear, conventional commit message like `feat(conductor): complete Phase X - <Phase Name>`.
    - **Summary Storage:** Include a detailed summary of all tasks completed in this phase within the commit message body.

6. **Record Phase Checkpoint SHA:**
    - Get the hash of the phase commit.
    - Update the phase heading in `plan.md` with `[checkpoint: <sha>]`.
    - Perform a final commit for the plan update: `chore(conductor): mark phase '<Phase Name>' as complete`.

7. **Announce Completion:** Inform the user that the phase is complete and successfully checkpointed. If the track is fully complete, prompt the user to push the branch and open a Pull Request.

## Development Commands (Maven/Spring Boot)

### Setup

```bash
# Install dependencies and build all modules (Quiet & Batch)
mvn -q -B clean install -DskipTests
```

### Daily Development

```bash
# Run a specific service (Quiet)
mvn -q -pl <service-name> spring-boot:run

# Run all tests (with formatting, Quiet)
mvn -q -B spotless:apply test

# Run a specific test (with formatting, Quiet)
mvn -q -B spotless:apply -pl <service-name> test -Dtest=<TestClassName>
```

### Before Checkpointing (Verification & Security)

```bash
# Enforce formatting, run static analysis, execute tests, and build (Quiet & Batch)
mvn -q -B spotless:check clean verify
```

## Definition of Done

A task is complete when:

1. All code implemented to specification.
2. **Tests written first** and passing.
3. Edge cases considered and verified.
4. Code coverage meets requirements (>80%).
5. Code follows project's code style guidelines, passes static analysis, and is formatted with Spotless.
6. Documentation updated if needed.
7. Commit messages adhere to Conventional Commits and development occurs on feature branches.
8. Task marked complete in `plan.md`.

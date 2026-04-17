# Project Workflow

## Guiding Principles

1. **The Plan is the Source of Truth:** All work must be tracked in `plan.md`
2. **The Tech Stack is Deliberate:** Changes to the tech stack must be documented in `tech-stack.md` *before* implementation
3. **Test-Driven Development (TDD):** **CRITICAL: Always write tests first.** Consider edge cases and boundary conditions for every feature. Consult the user if specific test cases could be overlooked.
4. **Formatting is Mandatory:** **CRITICAL: Always run `mvn spotless:apply` before building or running tests** to ensure code consistency.
5. **High Code Coverage:** Aim for >80% code coverage for all modules.
6. **User Experience First:** Every decision should prioritize user experience, following the minimalist and analytical guidelines.
7. **High-Signal Output:** **CRITICAL: Always use quiet flags (e.g., `mvn -q`) and batch mode (`-B`)** to minimize noise. Output must be limited to errors, failures, or critical architectural issues to maintain context efficiency.
8. **Non-Interactive & CI-Aware:** Prefer non-interactive commands. Use `mvn` with appropriate flags for CI-like behavior.

## Task Workflow

All tasks follow a strict lifecycle:

### Standard Task Workflow

1. **Select Task:** Choose the next available task from `plan.md` in sequential order.

2. **Mark In Progress:** Before beginning work, edit `plan.md` and change the task from `[ ]` to `[~]`.

3. **Write Failing Tests (Red Phase):**
   - Create a new test file or add to an existing one.
   - **Always write tests before implementation.**
   - **Consider Edge Cases:** Think about null inputs, empty collections, large numbers, and unauthorized access.
   - **Consult User:** If you're unsure about the coverage or edge cases, ask the user for specific scenarios.
   - **CRITICAL:** Apply formatting with `mvn -q spotless:apply`, then run the tests and confirm that they fail as expected.

4. **Implement to Pass Tests (Green Phase):**
   - Write the minimum amount of application code necessary to make the failing tests pass.
   - Apply formatting with `mvn -q spotless:apply`, then run the test suite again and confirm that all tests now pass.

5. **Refactor (Optional but Recommended):**
   - Refactor the implementation and test code for clarity and performance.
   - Apply formatting with `mvn -q spotless:apply`, then rerun tests to ensure they still pass.

6. **Verify Coverage:** Run coverage reports (e.g., `mvn -q jacoco:report`). Target: >80% coverage for new code.

7. **Document Deviations:** If implementation differs from the tech stack, update `tech-stack.md` and add a dated note before proceeding.

8. **Update Plan (Local):** Mark the task as complete `[x]` in `plan.md`. Since commits are per-phase, do not record a SHA yet.

### Phase Completion Verification and Checkpointing Protocol

**Trigger:** This protocol is executed immediately after all tasks in a phase are marked complete in `plan.md`.

1. **Announce Protocol Start:** Inform the user that the phase is complete and the verification protocol has begun.

2. **Ensure Test Coverage:**
    - Identify all files changed during the phase.
    - Verify that every new or modified code file has corresponding tests.
    - Create any missing tests, following the repository's naming and style conventions.

3. **Execute Automated Tests:**
    - Announce the test command (e.g., `mvn -q -B spotless:apply test`).
    - Apply formatting with `mvn -q spotless:apply`, then run the tests and address any failures (maximum two fix attempts before stopping for guidance).

4. **Propose Manual Verification Plan:**
    - Generate a step-by-step manual verification plan based on `product.md` and `plan.md`.
    - Wait for explicit user approval before proceeding.

5. **Create Phase Commit (Checkpoint):**
    - Stage all changes (code, tests, and `plan.md`).
    - **Commit Message:** Use a clear message like `feat(conductor): Complete Phase X - <Phase Name>`.
    - **Summary Storage:** Include a detailed summary of all tasks completed in this phase within the commit message body.

6. **Record Phase Checkpoint SHA:**
    - Get the hash of the phase commit.
    - Update the phase heading in `plan.md` with `[checkpoint: <sha>]`.
    - Perform a final commit for the plan update: `conductor(plan): Mark phase '<Phase Name>' as complete`.

7. **Announce Completion:** Inform the user that the phase is complete and successfully checkpointed.

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

### Before Checkpointing

```bash
# Run full build, checkstyle, and tests (Quiet & Batch)
mvn -q -B spotless:apply clean verify
```


## Definition of Done

A task is complete when:

1. All code implemented to specification.
2. **Tests written first** and passing.
3. Edge cases considered and verified.
4. Code coverage meets requirements (>80%).
5. Code follows project's code style guidelines and is formatted with Spotless.
6. Documentation updated if needed.
7. Task marked complete in `plan.md`.

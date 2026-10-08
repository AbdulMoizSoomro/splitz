# General Code Style Principles

This document outlines general coding principles that apply across all languages and frameworks used in this project.

## Readability

- Code should be easy to read and understand by humans.
- Avoid overly clever or obscure constructs.

## Consistency

- Follow existing patterns in the codebase.
- Maintain consistent formatting, naming, and structure.

## Simplicity

- Prefer simple solutions over complex ones.
- Break down complex problems into smaller, manageable parts.

## Maintainability

- Write code that is easy to modify and extend.
- Minimize dependencies and coupling.

## Documentation

- Document *why* something is done, not just *what*.
- Keep documentation up-to-date with code changes.

## Commits

- Write the subject in the imperative mood, scoped by area, e.g. `fix(expense): ...`, `refactor(frontend): ...`.
- Explain the reasoning in the body: what was wrong, why this approach, what was deliberately left out.
  Reference issue numbers when one exists.
- **Do not add attribution trailers.** In particular, never append `Co-Authored-By`, `Generated with`, or
  similar tool/AI authorship lines.
- Commit history here is single-author by convention. The existing commits carry no trailers, so adding
  one breaks the established convention and adds noise to every commit an agent produces.
- Do not add trailers to amend, rebase, or cherry-pick existing commits either — keep the message style of
  the history being extended.

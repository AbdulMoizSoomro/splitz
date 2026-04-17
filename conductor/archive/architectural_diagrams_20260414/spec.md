# Specification: Architectural Diagrams (Current State)

## Overview

Create a comprehensive suite of architectural diagrams using Mermaid.js to document the "as-built" state of the Splitz project. This documentation will serve as the source of truth for the system's structure, service interactions, and data models up to the current implementation (including Advanced Splits).

## Functional Requirements

- **System Context Diagram**: High-level view of users, services, and external dependencies.
- **Service Interaction & Security Diagram**: Visual representation of JWT-based auth flow and inter-service communication (Expense -> User).
- **Unified ERD**: Combined data model for both `user-service` and `expense-service`.
- **Logic Sequence Diagrams**: Detailed flow for complex operations like creating an expense with advanced split logic.
- **Organization**: All diagrams to be stored in the `docs/diagrams/` directory.

## Non-Functional Requirements

- **Maintainability**: Diagrams must be in Mermaid.js (text-based) for version control.
- **Accuracy**: Must reflect the current code and database schema (Flyway migrations).

## Acceptance Criteria

- [ ] All requested diagrams generated and verified.
- [ ] Diagrams render correctly in standard Mermaid.js viewers.
- [ ] Final documentation is linked or organized within `docs/diagrams/`.

## Out of Scope

- Infrastructure/Deployment diagrams (Docker, K8s).
- Future phase planning (Phase 4.5+ hardening).

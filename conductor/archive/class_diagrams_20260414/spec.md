# Specification: Comprehensive Class Diagrams (Current State)

## Overview

Create a thorough suite of Mermaid.js class diagrams for the Splitz project, covering all implemented services and shared modules. These diagrams will provide a detailed technical view of the system's class structures, relationships, and internal logic.

## Functional Requirements

- **Comprehensive Coverage**: Generate class diagrams for `user-service`, `expense-service`, and `common-security`.
- **Thorough Detail**: Include all fields, methods (private and public), and explicit relationships (inheritance, implementation, composition, and dependency).
- **Cross-Service Mapping**: Illustrate the logical relationships and communication patterns between services (e.g., `WebClient` usage).
- **Integration**: All diagrams will be stored in `docs/diagrams/` and linked from the existing `index.md`.

## Non-Functional Requirements

- **Consistency**: Use standardized Mermaid.js class diagram syntax.
- **Maintainability**: Ensure diagrams are readable and accurately reflect the current code state.
- **Skill Utilization**: MUST utilize the `mermaid-diagrams` skill for best practices in diagramming and the `java-springboot` skill for idiomatic Java/Spring Boot architectural mapping and standards.

## Acceptance Criteria

- Diagrams created for all core layers: Controllers, Services, DTOs, Models, Mappers, and Repositories.
- Diagrams correctly represent Java 21/Spring Boot 3.2 idioms (e.g., Records, @Autowired dependencies).
- Final documentation renders correctly in standard Mermaid.js viewers and is integrated into `docs/diagrams/index.md`.

## Out of Scope

- Future/Unimplemented features.
- Infrastructure configuration (Dockerfiles, Maven poms).
- External library internals (except where directly extended or heavily used).

# Architectural Visualization & Diagramming Principles

## Mandatory Skill Utilization
- **CRITICAL**: Utilize the `mermaid-diagrams` skill for all tasks involving the creation, modification, or optimization of Mermaid.js diagrams. This ensures adherence to advanced syntax and best practices.

## Design Standards
- **Vertical Orientation**: Prefer `direction TD` (Top-Down) for complex class diagrams and flowcharts to improve readability on standard screens.
- **Layered Organization**: Use `namespace` or `subgraph` blocks to visually group related components (e.g., Controller Layer, Service Layer).
- **Color Coding**: Apply consistent color schemes to distinguish architectural layers (e.g., specific fills for Models vs. Services).
- **Logical Relationships**: Prioritize clear relationship mapping (inheritance, composition, dependency) to maintain a true "as-built" representation of the code.

## Maintainability
- Diagrams must be stored as Markdown files in the `docs/diagrams/` directory.
- Always update the `docs/diagrams/index.md` file when adding new diagrams.

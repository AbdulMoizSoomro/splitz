# System Context Diagram

This diagram provides a high-level overview of the Splitz system, its users, and its internal components.

```mermaid
flowchart TD
    %% Node Definitions
    User(["Splitz User<br/>(Primary Actor)"])
    
    subgraph SplitzSystem ["Splitz System"]
        direction TB
        UserService[["User Service<br/>(Auth & Friends)"]]
        ExpenseService[["Expense Service<br/>(Groups & Splits)"]]
    end

    %% Relationships
    User -- "Registers, Logs in,<br/>Manages Friends" --> UserService
    User -- "Creates Groups,<br/>Records Expenses" --> ExpenseService
    
    ExpenseService -- "Verifies User Details<br/>& Permissions" --> UserService

    %% Styling for visibility
    style User fill:#f9f,stroke:#333,stroke-width:2px,color:#000
    style UserService fill:#bbf,stroke:#333,stroke-width:2px,color:#000
    style ExpenseService fill:#bbf,stroke:#333,stroke-width:2px,color:#000
    style SplitzSystem fill:#fff,stroke:#333,stroke-dasharray: 5 5,color:#000
```

## Key Components

- **Splitz User**: The primary actor who interacts with the system via a client application (not shown).
- **User Service**: The core service for identity management and social features.
- **Expense Service**: The primary business logic service for the splitting functionality.
- **Internal Interaction**: The Expense Service relies on the User Service to validate members and permissions.

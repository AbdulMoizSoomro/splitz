
# Unified Entity Relationship Diagram (ERD)

This diagram represents the combined data model of the Splitz system, including both the `user-service` and `expense-service` entities and their relationships.

```mermaid
erDiagram
    %% User Service Entities
    USER ||--o{ USER_ROLE : has
    ROLE ||--o{ USER_ROLE : assigned_to
    USER ||--o{ FRIENDSHIP : initiates
    USER ||--o{ FRIENDSHIP : receives

    USER {
        bigint id PK
        varchar username
        varchar email
        varchar first_name
        varchar last_name
        varchar password
        boolean enabled
        boolean verified
        timestamp created_at
    }

    ROLE {
        bigint id PK
        varchar name
    }

    USER_ROLE {
        bigint user_id PK, FK
        bigint role_id PK, FK
    }

    FRIENDSHIP {
        bigint id PK
        bigint requester_id FK
        bigint addressee_id FK
        varchar status
        timestamp created_at
    }

    %% Expense Service Entities
    GROUP ||--o{ GROUP_MEMBER : has
    GROUP ||--o{ EXPENSE : contains
    GROUP ||--o{ SETTLEMENT : recorded_in
    CATEGORY ||--o{ EXPENSE : classifies
    EXPENSE ||--o{ EXPENSE_SPLIT : divided_into

    GROUP {
        bigint id PK
        varchar name
        text description
        bigint created_by FK
        boolean is_active
        timestamp created_at
    }

    GROUP_MEMBER {
        bigint id PK
        bigint group_id FK
        bigint user_id FK
        varchar role
        timestamp joined_at
    }

    CATEGORY {
        bigint id PK
        varchar name
        varchar icon
        varchar color
        boolean is_default
    }

    EXPENSE {
        bigint id PK
        bigint group_id FK
        varchar description
        decimal amount
        varchar currency
        bigint paid_by FK
        bigint category_id FK
        date expense_date
        timestamp created_at
    }

    EXPENSE_SPLIT {
        bigint id PK
        bigint expense_id FK
        bigint user_id FK
        varchar split_type
        decimal split_value
        decimal share_amount
    }

    SETTLEMENT {
        bigint id PK
        bigint group_id FK
        bigint payer_id FK
        bigint payee_id FK
        decimal amount
        varchar status
        timestamp created_at
        timestamp settled_at
    }

    %% Cross-Service Logical Relationships
    USER ||--o{ GROUP : creates
    USER ||--o{ GROUP_MEMBER : participates
    USER ||--o{ EXPENSE : pays
    USER ||--o{ EXPENSE_SPLIT : owes
    USER ||--o{ SETTLEMENT : settles
```

## Entity Descriptions

### User Service

- **USER**: Core user identity and authentication data.
- **ROLE**: System-level roles (e.g., ROLE_USER, ROLE_ADMIN).
- **FRIENDSHIP**: Social connections between users with states (PENDING, ACCEPTED, etc.).

### Expense Service

- **GROUP**: Shared spaces for tracking collective expenses.
- **GROUP_MEMBER**: Junction table linking users to groups with specific roles (e.g., ADMIN, MEMBER).
- **CATEGORY**: Classification for expenses (e.g., Food, Travel).
- **EXPENSE**: A single transaction recorded within a group.
- **EXPENSE_SPLIT**: Detailed breakdown of how an expense is shared among users (supports fixed, percentage, or share-based logic).
- **SETTLEMENT**: Records of payments made between users to resolve debts within a group.

## Cross-Service Integration

The `expense-service` maintains logical references to `user-service` via `user_id` fields (e.g., `created_by`, `paid_by`, `payer_id`). These are not enforced by database-level foreign keys as the services reside in separate databases.

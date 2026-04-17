# Sequence Diagram: Advanced Split Logic

This diagram details the process of creating an expense with advanced split logic (EQUAL, EXACT, PERCENTAGE, SHARES, ADJUSTMENT).

```mermaid
sequenceDiagram
    autonumber
    
    participant User as Splitz User
    participant Controller as ExpenseController
    participant Service as ExpenseService
    participant RepoGroup as GroupRepository
    participant RepoMember as GroupMemberRepository
    participant RepoExpense as ExpenseRepository
    participant Mapper as ExpenseMapper

    User->>+Controller: POST /groups/{groupId}/expenses (CreateExpenseRequest)
    Note over User, Controller: Includes splits, splitType, amount, etc.

    Controller->>+Service: createExpense(groupId, request)

    Service->>+RepoGroup: findById(groupId)
    RepoGroup-->>-Service: Group entity

    Service->>+RepoMember: existsByGroupIdAndUserId(groupId, paidBy)
    RepoMember-->>-Service: true/false

    Note over Service: [ Business Logic ]
    Service->>Service: calculateSplits(expense, request)
    
    alt EQUAL
        Service->>Service: calculateEqualSplits(...)
    else EXACT
        Service->>Service: calculateExactSplits(...)
    else PERCENTAGE
        Service->>Service: calculatePercentageSplits(...)
    else SHARES
        Service->>Service: calculateSharesSplits(...)
    end

    Service->>+RepoExpense: save(expense)
    Note right of RepoExpense: Persists Expense and all ExpenseSplits
    RepoExpense-->>-Service: Saved Expense entity

    Service->>+Mapper: toDTO(expense)
    Mapper-->>-Service: ExpenseDTO

    Service-->>-Controller: ExpenseDTO
    Controller-->>-User: 201 Created (ExpenseDTO)
```

## Flow Description

1. **Request Initiation**: The user sends a request to create an expense, providing the amount, payer, and a list of splits with a specific `splitType`.
2. **Validation**: The service verifies that the group exists and that the payer is a member of that group.
3. **Split Calculation**: Depending on the `splitType`, the service invokes the corresponding calculation method:
    - **EQUAL**: Divides the amount evenly among all split participants, handling remainders by adjusting the first split.
    - **EXACT**: Verifies that the sum of provided exact amounts matches the total expense amount.
    - **PERCENTAGE**: Calculates shares based on percentages and ensures the total sum is 100%.
    - **SHARES**: Calculates amounts based on a proportional distribution of total shares.
4. **Persistence**: The `Expense` entity, containing the list of `ExpenseSplit` entities, is saved in a single transaction.
5. **Response**: The saved entity is mapped to a DTO and returned to the client.

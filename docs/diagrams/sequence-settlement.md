# Sequence Diagram: Settlement Flow

This diagram illustrates the lifecycle of a settlement between two group members, including creation, payment notification, and final confirmation.

```mermaid
sequenceDiagram
    autonumber
    
    participant Payer as Payer (User A)
    participant Payee as Payee (User B)
    participant Controller as SettlementController
    participant Service as SettlementService
    participant DB as Expense DB

    Note over Payer, Payee: [ 1. Create Settlement ]
    Payer->>+Controller: POST /settlements (groupId, payerId, payeeId, amount)
    Controller->>+Service: createSettlement(request)
    Service->>+DB: Save Settlement (status: PENDING)
    DB-->>-Service: Saved Settlement
    Service-->>-Controller: SettlementDTO
    Controller-->>-Payer: 201 Created (SettlementDTO)

    Note over Payer, Payee: [ 2. Mark as Paid ]
    Payer->>+Controller: PUT /settlements/{id}/mark-paid
    Controller->>+Service: markAsPaid(id, currentUserId)
    Service->>+DB: Update status to MARKED_PAID
    DB-->>-Service: Updated Settlement
    Service-->>-Controller: SettlementDTO
    Controller-->>-Payer: 200 OK (SettlementDTO)

    Note over Payer, Payee: [ 3. Confirm Settlement ]
    Payee->>+Controller: PUT /settlements/{id}/confirm
    Controller->>+Service: confirmSettlement(id, currentUserId)
    Service->>+DB: Update status to COMPLETED
    DB-->>-Service: Updated Settlement
    Service-->>-Controller: SettlementDTO
    Controller-->>-Payee: 200 OK (SettlementDTO)
```

## Flow Description

1.  **Creation**: A group member (the payer) creates a settlement record to indicate they intend to pay a debt to another member (the payee). The settlement starts in the `PENDING` state.
2.  **Payment Notification**: Once the payer has transferred the funds (outside of the system), they call the `mark-paid` endpoint. The status transitions to `MARKED_PAID`, and the `marked_paid_at` timestamp is recorded.
3.  **Confirmation**: The payee verifies they have received the funds and calls the `confirm` endpoint. The status transitions to `COMPLETED`, the `settled_at` timestamp is recorded, and the debt is considered officially resolved within the system.

## Business Rules

-   **Payer Authorization**: Only the designated payer can mark a settlement as paid.
-   **Payee Authorization**: Only the designated payee can confirm a settlement.
-   **Status Transition**: 
    -   `PENDING` -> `MARKED_PAID`
    -   `MARKED_PAID` -> `COMPLETED`
-   **Group Membership**: Both payer and payee must be active members of the group specified in the settlement.

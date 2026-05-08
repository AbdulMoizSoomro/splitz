# ADR 0002: Membership Lifecycle Module

## Status
Proposed

## Context
Membership logic (roles, removals, authorization) was previously scattered throughout `GroupService.java`. This led to shallow methods, duplicated checks, and inconsistent enforcement of domain invariants (e.g., "admin wars" protections and "zero balance" requirements for leaving).

## Decision
We will extract a **Membership Module** within the `expense-service`. This module will own the `GroupMember` lifecycle and enforce all governance and financial invariants.

### Key Invariants
- **Peer Authority**: Admins have authority over other Admins, but the Owner is untouchable.
- **Strict Settlement**: A user cannot leave or be removed unless their balance is zero and they have no pending settlements.
- **Ownership Continuity**: A group must always have exactly one Owner.

### Interface
The module will provide a high-leverage interface for all membership changes:
- `addMember(...)`
- `updateRole(...)`
- `transferOwnership(...)`
- `removeMember(...)`
- `leaveGroup(...)`

## Consequences
- **Locality**: All membership rules are now in one place, making them easier to audit and test.
- **Leverage**: `GroupService` is simplified to managing group metadata and initial creation.
- **Dependency**: The Membership Module depends on the `BalanceService` (or a future Debt Module) to verify the Settlement Invariant.

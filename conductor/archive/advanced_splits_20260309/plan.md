# Implementation Plan: Implement Advanced Split Logic and Enhanced Category Management

## Phase 1: Advanced Split Logic [checkpoint: 8910764]
- [x] Task: Implement `PERCENTAGE` split type in `ExpenseService` with validation.
- [x] Task: Implement `SHARES` split type in `ExpenseService` with validation.
- [x] Task: Add `ADJUSTMENT` split type support.
- [x] Task: Update `ExpenseServiceTest` to cover new split logic and edge cases (e.g., rounding remainders).
- [x] Task: Conductor - User Manual Verification 'Phase 1: Advanced Split Logic' (Protocol in workflow.md)

## Phase 2: Enhanced Category Management [checkpoint: 8ceb849]
- [x] Task: Update `Category` entity and `CategoryDTO` to include `icon` and `color` fields.
- [x] Task: Implement CRUD operations in `CategoryService` (Create, Update, Delete).
- [x] Task: Implement `CategoryController` endpoints for managing categories.
- [x] Task: Create a Flyway migration to seed the database with default categories (Food, Rent, Travel, etc.).
- [x] Task: Write unit tests for `CategoryService`.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Enhanced Category Management' (Protocol in workflow.md)

## Phase 3: Integration & User Context [checkpoint: 266be42]
- [x] Task: Update `UserClient` to support fetching multiple users by ID if not already available.
- [x] Task: Modify `BalanceService` to enrich balance reports with user profile details (name, email) using `UserClient`.
- [x] Task: Update `GroupBalanceResponseDTO` and `UserBalanceResponseDTO` to include user profile info.
- [x] Task: Write integration tests for the enriched balance reports.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Integration & User Context' (Protocol in workflow.md)

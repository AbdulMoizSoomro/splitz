# Implementation Plan: Add Pagination to getAllUsers() and List Endpoints

## Phase 1: Setup & Testing Core Logic

- [ ] Task: Write tests for `UserController.getAllUsers()` with pagination and hard limits.
- [ ] Task: Write tests for `UserService.getAllUsers()` with pagination logic.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Setup & Testing Core Logic' (Protocol in workflow.md)

## Phase 2: Implement Pagination for getAllUsers

- [ ] Task: Update `UserController.getAllUsers()` to accept a `Pageable` parameter and return `Page<UserDTO>`.
- [ ] Task: Enforce hard limit on the `Pageable` size parameter in `UserController.getAllUsers()`.
- [ ] Task: Update `UserService.getAllUsers()` to return `Page<UserDTO>` using `Pageable`.
- [ ] Task: Update the `UserRepository` to support pagination if necessary (e.g., using `findAll(Pageable)`).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Implement Pagination for getAllUsers' (Protocol in workflow.md)

## Phase 3: Implement Pagination for Other List Endpoints (e.g., Search)

- [ ] Task: Write tests for `UserController.searchUsers()` with pagination and hard limits.
- [ ] Task: Write tests for `UserService.searchUsers()` with pagination logic.
- [ ] Task: Update `UserController.searchUsers()` to accept `Pageable` and return `Page<UserDTO>`.
- [ ] Task: Enforce hard limit on the `Pageable` size parameter in `UserController.searchUsers()`.
- [ ] Task: Update `UserService.searchUsers()` to return `Page<UserDTO>` using `Pageable`.
- [ ] Task: Update `UserRepository` to support paginated search (e.g., `findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(..., Pageable)`).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Implement Pagination for Other List Endpoints (e.g., Search)' (Protocol in workflow.md)

## Phase 4: Final Review & Integration

- [ ] Task: Run full test suite with formatting (`mvn spotless:apply clean verify`) to ensure no regressions.
- [ ] Task: Update API documentation (Swagger/OpenAPI) annotations on controllers to reflect paginated responses and limit constraints.
- [ ] Task: Review `user-service` to ensure all list endpoints are now paginated.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Final Review & Integration' (Protocol in workflow.md)
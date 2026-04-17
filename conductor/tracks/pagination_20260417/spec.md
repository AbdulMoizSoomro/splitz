# Specification: Add Pagination to getAllUsers() and List Endpoints

## Overview
Implement pagination across all user-related endpoints that return lists (e.g., `GET /users`, `GET /users/search`) to prevent OutOfMemory (OOM) errors and improve performance when dealing with large datasets. This addresses the critical production blocker CRITICAL-002.

## Functional Requirements
- **Pagination Support**: Modify `UserController.getAllUsers()` and related list endpoints (like user search) to accept Spring Data `Pageable` parameters (`page`, `size`, `sort`).
- **Response Format**: Endpoints must return a `Page<UserDTO>` (or a custom paginated response object) instead of a `List<UserDTO>`. This response must include metadata such as `totalElements`, `totalPages`, and `currentPage`.
- **Hard Limit Enforcement**: Enforce a maximum page size limit (e.g., max 100 items per page) to prevent abuse and ensure system stability.

## Non-Functional Requirements
- **Performance**: Pagination should significantly reduce the memory footprint and response time for endpoints returning large numbers of users.
- **Maintainability**: Ensure the implementation adheres to the existing Spring Boot and Java 21 conventions used in the project.
- **Standards**: Must follow TDD and utilize the `java-springboot` skill guidelines.

## Acceptance Criteria
- `GET /users?page=0&size=20` returns a paginated response with a maximum of 20 users.
- The paginated response includes accurate metadata (`totalElements`, `totalPages`, `number`, `size`).
- Requests requesting a page size larger than the hard limit (e.g., `size=500`) are either rejected with a `400 Bad Request` or capped at the maximum limit.
- Comprehensive tests (including MockMvc integration tests) are written covering edge cases like requesting an empty page, an out-of-bounds page, and enforcing the size limit.
- All existing tests pass.

## Out of Scope
- Pagination for endpoints outside of the `user-service`.
- Complex cursor-based pagination (keyset pagination); standard offset-based pagination is sufficient for this requirement.
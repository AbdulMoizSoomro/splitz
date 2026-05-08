# ADR 0001: Shared Security Authorizer

## Status
Accepted

## Context
Each microservice (e.g., `user-service`, `expense-service`) requires authorization logic to verify if a user has permission to access or modify a resource. Previously, this logic was implemented in each service, often requiring database lookups to resolve user IDs from usernames (e.g., `SecurityExpressions.java` in `user-service`). This created tight coupling between security logic and the database, and led to duplication of boilerplate code.

## Decision
We will centralize stateless authorization logic in a **Shared Security Authorizer** module within the `common-security` library.

### Key Implementation Details:
1. **Stateless Identity**: The JWT token will be modified to include the `userId` as a custom claim. This allows the authorizer to resolve the acting user's ID directly from the token without a database round-trip.
2. **Standardized Roles**: All roles will follow the Spring Security `ROLE_` prefix convention (e.g., `ROLE_ADMIN`). The authorizer will strictly enforce this.
3. **Separation of Ownership**:
    - **Identity-based Ownership**: Handled by the Shared Security Authorizer using ID equality and role checks.
    - **Resource-based Ownership**: Handled within the specific microservice's Service layer, where domain-specific relationships (e.g., group membership) are available.
4. **Deep Interface**: The authorizer will provide a simple interface (e.g., `isSelfOrAdmin(targetId)`) that hides the complexity of token parsing and security context interaction.

## Consequences

### Positive
- **Locality**: Security rules and role naming conventions are centralized in one place.
- **Performance**: Reduced database load by resolving identity via JWT claims.
- **Consistency**: Standardized RBAC across all microservices.
- **Testability**: Security logic can be unit-tested in isolation within `common-security`.

### Negative
- **Token Size**: Slightly increased JWT size due to the additional `userId` claim.
- **Dependency**: All services must depend on `common-security` and its updated authorizer.

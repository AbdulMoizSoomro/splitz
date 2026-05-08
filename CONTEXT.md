# Project Context

This file serves as the primary source of truth for the Splitz project's domain language and architecture.

## Domain Language

- **User**: A person who can create and participate in groups.
- **Group**: A collection of users who share expenses.
- **Expense**: A financial transaction where one or more users pay and the cost is split among group members.
- **Settlement**: A transaction to resolve debts between users.
- **Friend Request**: An invitation sent from one user to another to establish a connection.
- **Cancellation**: The act of revoking a sent friend request before it is accepted or rejected, resulting in the removal of the request record.
- **Shared Security Authorizer**: A centralized module in `common-security` that provides stateless authorization logic (e.g., identity checks, role verification) across all microservices.
- **Identity-based Ownership**: A security check verifying if the acting user is the owner of a resource by comparing user IDs (e.g., a user modifying their own profile).
- **Resource-based Ownership**: Domain-specific security logic verifying if a user has rights to a resource based on complex relationships (e.g., being a group admin or the payer of an expense).
- **Group Governance Setting**: Configuration within a Group that defines the permissions and capabilities of regular members. For the MVP, this specifically controls whether users with the `MEMBER` role can manage (add/remove) other members. Users with `OWNER` or `ADMIN` roles are exempt from these restrictions.
- **Temp Friend**: A user within a group who is not in the current user's friend list. They are visually distinguished with a badge and are temporarily "tracked" for the purpose of expense splitting and balance settlement. They appear in a dedicated "Temporary" section on the global Friends page so they don't pollute the actual friend list, and remain pinned there with no option to hide or remove them while a balance exists. Once all mutual debts are cleared (balance reaches zero), they automatically drop off the list.

## Architecture

Splitz is a microservices-based application.

- **user-service**: Manages user profiles, roles, and authentication.
- **expense-service**: Handles groups, expenses, categories, and settlements.
- **common-security**: Shared library for security configurations and utilities.
- **frontend-user**: React/TypeScript web application for end-users to manage expenses, groups, and friends.

## Environments

- **Dev**: Local development environment. Services run independently. Backend services use H2 in-memory databases for speed and zero-setup.
- **Integrated**: High-fidelity local environment using Docker Compose. Services run in containers. Uses a shared PostgreSQL instance with persistent volumes to simulate production behavior and support E2E testing.
- **Prod**: Production environment. Managed infrastructure with persistent PostgreSQL.

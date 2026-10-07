# Project Context

This file serves as the primary source of truth for the Splitz project's domain language and architecture.

## Domain Language

- **User**: A person who can create and participate in groups.
- **Group**: A collection of users who share expenses.
- **Expense**: A financial transaction where one or more users pay and the cost is split among group members.
- **Payment**: An actual transaction representing the transfer of money from a payer to a payee (with statuses: `PENDING`, `MARKED_PAID`, `COMPLETED`).
- **Settlement Allocation**: An assignment of a portion of a **Payment** to a specific group (or globally) to resolve a portion of debt between users.
- **Friend Request**: An invitation sent from one user to another to establish a connection.
- **Cancellation**: The act of revoking a sent friend request before it is accepted or rejected, resulting in the removal of the request record.
- **Shared Security Authorizer**: A centralized module in `common-security` that provides stateless authorization logic (e.g., identity checks, role verification) across all microservices.
- **Identity-based Ownership**: A security check verifying if the authenticated user is the owner of a resource by comparing user IDs (e.g., a user modifying their own profile).
- **Resource-based Ownership**: Domain-specific security logic verifying if a user has rights to a resource based on complex relationships (e.g., being a group admin or the payer of an expense).
- **Group Governance Setting**: Configuration within a Group that defines the permissions and capabilities of regular members. For the MVP, this specifically controls whether users with the `MEMBER` role can manage (add/remove) other members, including bulk addition of **Friends** and **Temp Friends**. Users with `OWNER` or `ADMIN` roles are exempt from these restrictions. In terms of authority, `ADMIN` users have **Peer Removal Authority** (meaning they can remove other `ADMIN` users), but only the `OWNER` can demote an `ADMIN` user to a `MEMBER`. The `OWNER` is the only role that cannot be demoted or removed by others.
- **Settled Membership Invariant**: A policy invariant enforced by the Membership Module: a user cannot leave a group, nor can they be removed by an Admin, while they have a non-zero balance or any `PENDING` or `MARKED_PAID` settlements. While strictly enforced for the MVP to ensure financial integrity, the architecture allows for future relaxation where debt could "follow" a user into their personal activity after leaving.
- **Ownership Invariant**: A group must always have exactly one Owner. The Owner role cannot be demoted or removed. To leave a group, an Owner must first transfer their ownership to another member.
- **Account Simplification Opt-Out**: A user's account-level preference to be excluded from debt netting in **every** group they belong to. It is a **hard override**, not an inherited default: once set, it excludes the user even from groups configured before the opt-out existed, so a group's pre-existing configuration can never weaken consent the user already gave. Opting back in requires turning the account-level opt-out off and then using the per-group control. A group's **Effective Opt-Out Set** is the union of its own configured opt-outs with the account-level opt-outs of its current members; it is what the Debt Simplification Plan computes against, and it is reported whether or not netting is enabled for the group. See ADR 0003.
- **Temp Friend**: A user who shares at least one group with the current user but is not in their friend list, and with whom the current user has a non-zero balance. They are "tracked" for the purpose of expense splitting and balance settlement, and appear on the global Friends page inside the **same unified connections list** as confirmed friends, not in a section of their own. They are visually distinguished by an orange avatar badge and card styling rather than by placement in a separate list. While a balance exists they cannot be hidden or removed from that list: the per-connection action menu (which carries "View Details" and "Remove Friend") is rendered only for confirmed friends, so a Temp Friend exposes only the friend-request controls — "Add Friend", or "Cancel Request" once an outgoing request exists. A Temp Friend is included only while `|balance| > 0.01` (`MONEY_TOLERANCE`), so once all mutual debts are cleared they drop off the list automatically. The Friends page filters are `ALL`, `YOU_OWE`, `OWED_TO_YOU`, and `SETTLED`; there is deliberately no `TEMPORARY` filter, so Temp Friends are selected by their balance rather than by a separate category.

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

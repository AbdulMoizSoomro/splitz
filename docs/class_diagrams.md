# Splitz System Class Diagrams

This document contains comprehensive class diagrams for the three primary components of the Splitz application: `user-service`, `expense-service`, and `common-security`. It concludes with a high-level bird's eye view connecting all domains.

## 1. User Service
The `user-service` is responsible for handling user authentication, profile management, role-based access, and friendship networks.

```mermaid
classDiagram
    class UserController {
        +registerUser(UserDTO)
        +authenticateUser(AuthRequest)
        +getUser(UUID)
        +updateUser(UUID, UpdateUserDTO)
        +deleteUser(UUID)
        +searchUsers(String)
    }

    class FriendshipController {
        +sendRequest(UUID, UUID)
        +getFriends(UUID)
        +getPendingRequests(UUID)
        +acceptRequest(UUID, UUID)
        +rejectRequest(UUID, UUID)
        +removeFriend(UUID, UUID)
    }
    
    class RoleController {
        +createRole(RoleDTO)
        +getRoles()
    }

    class UserService {
        +createUser(User)
        +findUserById(UUID)
        +updateUser(UUID, User)
        +deleteUser(UUID)
        +searchUsers(String)
    }

    class FriendshipService {
        +sendFriendRequest(UUID, UUID)
        +acceptFriendRequest(UUID, UUID)
        +rejectFriendRequest(UUID, UUID)
        +removeFriend(UUID, UUID)
        +getFriends(UUID)
        +getPendingRequests(UUID)
    }

    class UserRepository {
        <<interface>>
        +findByUsername(String)
        +findByEmail(String)
    }

    class FriendshipRepository {
        <<interface>>
        +findByRequesterAndAddressee()
        +findByUserAndStatus()
    }

    class User {
        +UUID id
        +String username
        +String email
        +String password
        +Set roles
    }

    class Role {
        +UUID id
        +String name
    }

    class Friendship {
        +UUID id
        +User requester
        +User addressee
        +FriendshipStatus status
        +LocalDateTime createdAt
    }

    class FriendshipStatus {
        <<enumeration>>
        PENDING
        ACCEPTED
        REJECTED
        BLOCKED
    }

    UserController --> UserService
    FriendshipController --> FriendshipService
    RoleController --> RoleService
    UserService --> UserRepository
    FriendshipService --> FriendshipRepository
    FriendshipService --> UserRepository
    User *-- Role
    Friendship --> User : requester / addressee
    Friendship --> FriendshipStatus
```

## 2. Expense Service
The `expense-service` tracks groups, manages shared expenses, calculates splits among members, and records settlement payments. It operates using logical references to user IDs, keeping domains loosely coupled.

```mermaid
classDiagram
    class GroupController
    class ExpenseController
    class SettlementController
    class CategoryController
    class BalanceController
    class MembershipController

    class GroupService
    class ExpenseService
    class PaymentService
    class MembershipService
    class BalanceService
    class CategoryService

    class Group {
        +UUID id
        +String name
        +String description
        +Set members
        +List expenses
    }

    class GroupMember {
        +UUID id
        +UUID userId
        +Group group
        +GroupRole role
        +BigDecimal balance
    }

    class Expense {
        +UUID id
        +Group group
        +UUID payerId
        +BigDecimal totalAmount
        +Category category
        +List splits
    }

    class ExpenseSplit {
        +UUID id
        +Expense expense
        +UUID userId
        +BigDecimal amount
        +SplitType splitType
    }

    class Payment {
        +UUID id
        +UUID payerId
        +UUID payeeId
        +BigDecimal amount
        +SettlementStatus status
    }

    class SettlementAllocation {
        +UUID id
        +Payment payment
        +UUID groupId
        +BigDecimal allocatedAmount
    }
    
    class Category {
        +UUID id
        +String name
    }

    class GroupRole {
        <<enumeration>>
        OWNER
        ADMIN
        MEMBER
    }

    class SplitType {
        <<enumeration>>
        EQUAL
        EXACT
        PERCENTAGE
    }
    
    class SettlementStatus {
        <<enumeration>>
        PENDING
        MARKED_PAID
        COMPLETED
    }

    GroupController --> GroupService
    ExpenseController --> ExpenseService
    SettlementController --> PaymentService
    BalanceController --> BalanceService
    MembershipController --> MembershipService
    CategoryController --> CategoryService

    GroupService --> Group
    ExpenseService --> Expense
    PaymentService --> Payment

    Group *-- GroupMember
    Group *-- Expense
    Expense *-- ExpenseSplit
    Expense --> Category
    GroupMember --> GroupRole
    ExpenseSplit --> SplitType
    Payment *-- SettlementAllocation
    Payment --> SettlementStatus
```

## 3. Common Security
The `common-security` module is shared across microservices. It intercepts HTTP requests, parses JWT tokens, and evaluates domain-driven security rules (like Identity-based and Resource-based Ownership).

```mermaid
classDiagram
    class SharedSecurityAuthorizer {
        +isOwner(UUID) boolean
        +isGroupAdmin(UUID) boolean
        +hasRole(String) boolean
    }

    class JwtUtil {
        +generateToken(UserDetails) String
        +validateToken(String, UserDetails) boolean
        +extractUsername(String) String
        +extractClaims(String) Claims
    }

    class JwtRequestFilter {
        +doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
        -extractJwtFromRequest(HttpServletRequest) String
    }
    
    JwtRequestFilter --> JwtUtil : uses for token validation
    SharedSecurityAuthorizer ..> JwtRequestFilter : relies on SecurityContext
```

## 4. Bird's Eye View (Whole System)
This macroscopic diagram links together the core entities across all the services. Note how the `expense-service` entities hold a `userId` referencing the `User` entity from the `user-service`.

```mermaid
classDiagram
    %% Core Users
    class User {
        +UUID id
        +String username
        +String email
    }
    class Friendship {
        +UUID requesterId
        +UUID addresseeId
        +FriendshipStatus status
    }
    
    %% Core Expenses
    class Group {
        +UUID id
        +String name
    }
    class GroupMember {
        +UUID userId
        +GroupRole role
        +BigDecimal balance
    }
    class Expense {
        +UUID payerId
        +BigDecimal amount
    }
    class ExpenseSplit {
        +UUID userId
        +BigDecimal amount
    }
    class Payment {
        +UUID payerId
        +UUID payeeId
        +BigDecimal amount
    }
    class SettlementAllocation {
        +UUID groupId
    }
    
    %% Common Security
    class SharedSecurityAuthorizer {
        +isOwner(userId)
    }

    %% Entity Relational links
    User "1" -- "*" Friendship : participates in
    Group "1" *-- "*" GroupMember : contains
    Group "1" *-- "*" Expense : contains
    Expense "1" *-- "*" ExpenseSplit : divided into
    Payment "1" *-- "*" SettlementAllocation : allocated to

    %% Cross-domain Logical References (Microservice boundaries)
    GroupMember ..> User : references userId
    Expense ..> User : references payerId
    ExpenseSplit ..> User : references userId
    Payment ..> User : references payer/payee ID
    
    %% Security Rules Enforcement
    SharedSecurityAuthorizer ..> User : validates access ownership
    SharedSecurityAuthorizer ..> GroupMember : validates resource rights
```

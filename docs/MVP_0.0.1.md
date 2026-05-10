# SPLITZ MVP 0.0.1 Specification

> **Version:** 0.0.1  
> **Target Date:** ~2 weeks from start  
> **Status:** In Development

---

## MVP Definition

**What is MVP 0.0.1?**  
The minimum viable product that allows a small group of friends to track shared expenses and see who owes whom. No payment integration, no mobile app — just core expense splitting functionality.

**Success Criteria:**  
A user can register, login, create a group with friends, add expenses, and see calculated balances.

---

## In Scope ✅

### User Service (Port 8080)

| Feature | Description | Status |
|---------|-------------|--------|
| Registration | Create account with email/username/password | ✅ Done |
| Login | JWT-based authentication | ✅ Done |
| Profile | View/update own profile | ✅ Done |
| User Search | Find users by name/email | ✅ Done |
| Friendships | Send/accept/reject friend requests | ✅ Done |

### Expense Service (Port 8081)

| Feature | Description | Status |
|---------|-------------|--------|
| Categories | Predefined expense categories | ✅ Done |
| Groups | Create/manage expense groups | ✅ Done |
| Group Members | Add/remove members from groups (Leave Group with debt blocking), role visualization and management | ✅ Done |
| Expenses | Create/edit/delete expenses | ✅ Done |
| Split Types | EQUAL and EXACT splits | ✅ Done |
| Balances | Calculate who owes whom | ✅ Done |
| Settlements | Record debt payments (manual) | ✅ Done |

### Infrastructure

| Feature | Description | Status |
|---------|-------------|--------|
| CI Pipeline | GitHub Actions build/test | ✅ Done |
| API Docs | Swagger UI for both services | ✅ Done |
| Docker | Dockerfile for local development | ✅ Done |

---

## Out of Scope ❌

These features are explicitly **NOT** part of MVP 0.0.1:

| Feature | Reason | Target Version |
|---------|--------|----------------|
| Payment Integration | Complexity; manual settlements first | v0.1.0 |
| PERCENTAGE Splits | EQUAL/EXACT covers most cases | v0.1.0 |
| Recurring Expenses | Nice-to-have, not essential | v0.1.0 |
| Email Notifications | No email service configured | v0.1.0 |
| Receipt Upload | Requires file storage (S3) | v0.1.0 |
| Multi-currency | EUR only for MVP | v0.1.0 |
| Mobile App | Web API only | v1.0.0 |
| API Gateway | Direct service calls for MVP | v0.2.0 |
| gRPC | REST is sufficient for MVP | v0.2.0 |
| Kubernetes | Docker Compose is enough | v1.0.0 |
| Analytics/Reports | Basic balances first | v0.1.0 |

---

## User Journeys

### Journey 1: New User Registration

```
1. User visits /users (POST) with email, username, password
2. Account created with ROLE_USER
3. User logs in via /authenticate
4. Receives JWT token for future requests
```

### Journey 2: Adding a Friend

```
1. User searches for friend via /users/search?query=john
2. User sends friend request POST /users/{myId}/friends
3. Friend sees pending request GET /users/{friendId}/friends/requests
4. Friend accepts PUT /users/{friendId}/friends/{requestId}/accept
5. Both users now see each other in friends list
```

### Journey 3: Creating a Group Expense

```
1. User creates group POST /groups { name: "Roommates" }
2. User adds friends POST /groups/{id}/members { userId: 123 }
3. User creates expense POST /groups/{id}/expenses
   {
     description: "Groceries",
     amount: 60.00,
     paidBy: currentUserId,
     splitType: "EQUAL",
     splitAmong: [user1, user2, user3]
   }
4. System calculates: each person owes 20.00
5. User views balances GET /groups/{id}/balances
   → Payer: +40.00, Others: -20.00 each
```

### Journey 4: Settling Up

```
1. User B owes User A $20
2. User B pays User A (outside app - cash/Venmo/etc)
3. User B creates settlement POST /settlements
   { groupId, payerId: B, payeeId: A, amount: 20.00 }
4. User B marks as paid PUT /settlements/{id}/mark-paid
5. User A confirms PUT /settlements/{id}/confirm
6. Balances updated to reflect settlement
```

---

## API Contracts (Core)

### Authentication

**POST /authenticate**

```json
// Request
{ "username": "john", "password": "secret123" }

// Response 200
{ "token": "eyJhbGciOiJIUzI1NiIs..." }
```

### Groups

**POST /groups**

```json
// Request (Auth required)
{ 
  "name": "Apartment 4B",
  "description": "Monthly shared expenses"
}

// Response 201
{
  "id": 1,
  "name": "Apartment 4B",
  "description": "Monthly shared expenses",
  "createdBy": 42,
  "members": [{ "userId": 42, "role": "ADMIN" }],
  "createdAt": "2025-01-01T10:00:00Z"
}
```

### Expenses

**POST /groups/{groupId}/expenses**

```json
// Request (Auth required, must be group member)
{
  "description": "Electric bill",
  "amount": 150.00,
  "currency": "EUR",
  "paidBy": 42,
  "categoryId": 4,
  "expenseDate": "2025-01-15",
  "splitType": "EQUAL",
  "splitAmong": [42, 43, 44]
}

// Response 201
{
  "id": 1,
  "description": "Electric bill",
  "amount": 150.00,
  "paidBy": 42,
  "splits": [
    { "userId": 42, "shareAmount": 50.00 },
    { "userId": 43, "shareAmount": 50.00 },
    { "userId": 44, "shareAmount": 50.00 }
  ],
  "createdAt": "2025-01-15T14:30:00Z"
}
```

### Balances

**GET /groups/{groupId}/balances**

```json
// Response 200
{
  "groupId": 1,
  "balances": [
    { "userId": 42, "balance": 100.00 },  // owed by others
    { "userId": 43, "balance": -50.00 },  // owes others
    { "userId": 44, "balance": -50.00 }
  ],
  "simplifiedDebts": [
    { "from": 43, "to": 42, "amount": 50.00 },
    { "from": 44, "to": 42, "amount": 50.00 }
  ]
}
```

---

## Data Models

### User Service

```
User
├── id: Long (PK)
├── username: String (unique)
├── email: String (unique)
├── password: String (hashed)
├── firstName: String
├── lastName: String
├── enabled: boolean
├── verified: boolean
├── roles: Set<Role>
├── createdAt: DateTime
└── updatedAt: DateTime

Friendship
├── id: Long (PK)
├── requester: User (FK)
├── addressee: User (FK)
├── status: PENDING | ACCEPTED | REJECTED | BLOCKED
├── createdAt: DateTime
└── updatedAt: DateTime
```

### Expense Service

```
Group
├── id: Long (PK)
├── name: String
├── description: String
├── createdBy: Long (userId)
├── isActive: boolean
├── allowMembersToManageMembers: boolean
├── createdAt: DateTime
└── updatedAt: DateTime

GroupMember
├── id: Long (PK)
├── group: Group (FK)
├── userId: Long
├── role: ADMIN | MEMBER
└── joinedAt: DateTime

Category
├── id: Long (PK)
├── name: String
├── icon: String
├── color: String
├── defaultCategory: boolean
└── createdAt: DateTime

Expense
├── id: Long (PK)
├── group: Group (FK)
├── description: String
├── amount: BigDecimal
├── currency: String (default EUR)
├── paidBy: Long (userId)
├── category: Category (FK)
├── expenseDate: Date
├── notes: String
├── createdAt: DateTime
└── updatedAt: DateTime

ExpenseSplit
├── id: Long (PK)
├── expense: Expense (FK)
├── userId: Long
├── splitType: EQUAL | EXACT
├── splitValue: BigDecimal
├── shareAmount: BigDecimal
└── isPaid: boolean

Settlement
├── id: Long (PK)
├── group: Group (FK)
├── payerId: Long (userId)
├── payeeId: Long (userId)
├── amount: BigDecimal
├── status: PENDING | MARKED_PAID | COMPLETED
├── createdAt: DateTime
├── markedPaidAt: DateTime
└── settledAt: DateTime
```

---

## Default Categories (Seeded)

| ID | Name | Icon | Color |
|----|------|------|-------|
| 1 | Food & Dining | 🍕 | #FF6B6B |
| 2 | Transport | 🚗 | #4ECDC4 |
| 3 | Entertainment | 🎬 | #45B7D1 |
| 4 | Utilities | 💡 | #96CEB4 |
| 5 | Shopping | 🛒 | #FFEAA7 |
| 6 | Other | 📦 | #DFE6E9 |

---

## Technical Requirements

### Performance Targets

- API response time: < 500ms (p95)
- Concurrent users: 100 (dev/staging)
- Database: H2 (dev), PostgreSQL (prod-ready)

### Security Requirements

- All passwords BCrypt hashed
- JWT tokens expire in 24 hours
- Stateless Identity: userId and roles included in JWT claims for DB-less authorization ✅
- Shared Security Authorizer: Centralized logic for role and owner-based checks in common-security ✅
- HTTPS required in production
- No sensitive data in logs

### Quality Requirements

- Test coverage: ≥60%
- All endpoints have integration tests
- No critical/high security vulnerabilities

---

## Milestones

| Milestone | Stories | Est. Days | Exit Criteria |
|-----------|---------|-----------|---------------|
| **M1: DevOps Setup** | S01-S03 | 2 | CI runs, coverage reported, Docker works |
| **M2: User Service Complete** | S04-S07 | 3 | Friendships work, Swagger available |
| **M3: Expense Service Core** | S09-S13 | 5 | Groups, expenses, splits work |
| **M4: MVP Complete** | S14-S15 | 2 | Balances calculated, settlements tracked |

**Total: ~12 working days**

---

## Release Checklist

Before tagging v0.0.1:

- [ ] All Stories S01-S15 complete
- [ ] All tests passing
- [ ] Test coverage ≥60%
- [ ] Swagger UI works for both services
- [ ] Docker build succeeds
- [ ] README updated with setup instructions
- [ ] No hardcoded secrets (use env vars)
- [ ] Manual E2E test: register → login → group → expense → balance
- [ ] Tag: `git tag -a v0.0.1 -m "MVP release"`

---

## Post-MVP (v0.1.0 Preview)

After MVP is stable, next priorities:

1. PERCENTAGE split type
2. Email notifications (welcome, expense added)
3. Receipt URL handling
4. Export to CSV
5. Recurring expenses
6. Multi-currency support

---

*Document maintained by: Soomro*  
*Last updated: December 31, 2025*

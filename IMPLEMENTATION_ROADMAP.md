# SPLITZ Implementation Roadmap

> **Last Updated:** December 31, 2025  
> **Goal:** Splitwise-like expense splitting app for friends and roommates  
> **Approach:** Stories → Tasks, Test-First, ~1 day per story

---

## Quick Reference

### Branch Naming
```
feature/<story-id>-<short-description>
Example: feature/S01-github-actions-ci
```

### Definition of Done (DoD)
- [ ] Code compiles without warnings
- [ ] All new code has tests (written first when possible)
- [ ] All tests pass (`mvn test`)
- [ ] Self-review completed
- [ ] Documentation updated if public API changed

### Story Status Legend
- ⬜ Not Started
- 🟡 In Progress
- ✅ Complete
- ⏸️ Blocked/Deferred

---

## Current State Summary

| Service | Status | What Works |
|---------|--------|------------|
| user-service | ~80% Phase 1 | Auth, User CRUD, Search, Roles, Flyway |
| expense-service | 0% | Stub only |
| DevOps | 0% | No CI/CD, No Docker |

**What's Missing for MVP:**
1. Friendship API (user-service)
2. CI/CD pipeline
3. Entire expense-service
4. API documentation (OpenAPI)
5. Docker setup

---

## Phase 0: DevOps Foundation ⬜
> **Goal:** Automated quality gates before adding more features  
> **Duration:** ~2 days

### Story S01: GitHub Actions CI Pipeline ⬜
> Branch: `feature/S01-github-actions-ci`

**Why:** Every commit should be validated automatically. Catch issues early.

**Acceptance Criteria:**
- [ ] Push to `main` or PR triggers build
- [ ] Maven build + test runs
- [ ] Build fails if tests fail
- [ ] Badge shows build status in README

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T01.1 | Create `.github/workflows/ci.yml` with Maven build | 2h | Trigger on push/PR to main |
| T01.2 | Add build status badge to README | 15m | |

**Test Strategy:** CI itself is the test — verify a failing test breaks the build.

**Files to Create/Modify:**
- `.github/workflows/ci.yml` (new)
- `README.md` (add badge)

---

### Story S02: Test Coverage Reporting ⬜
> Branch: `feature/S02-test-coverage`

**Why:** Know what's tested, prevent coverage regression.

**Acceptance Criteria:**
- [ ] JaCoCo generates coverage report
- [ ] Coverage report visible in CI logs or artifact
- [ ] Minimum 60% line coverage enforced (fail build if below)

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T02.1 | Add JaCoCo plugin to user-service pom.xml | 1h | Configure report generation |
| T02.2 | Set coverage threshold (60% lines) | 30m | Build fails if below |
| T02.3 | Update CI to publish coverage report | 1h | Upload as artifact |

**Test Strategy:** Intentionally drop coverage below threshold, verify build fails.

**Files to Modify:**
- `user-service/pom.xml`
- `.github/workflows/ci.yml`

---

### Story S03: Basic Dockerfile for User Service ⬜
> Branch: `feature/S03-user-service-dockerfile`

**Why:** Reproducible builds, prep for compose and deployment.

**Acceptance Criteria:**
- [ ] `docker build` succeeds
- [ ] Container starts and responds to health endpoint
- [ ] Image uses non-root user
- [ ] Image size under 400MB

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T03.1 | Create multi-stage Dockerfile | 2h | Build stage + runtime stage |
| T03.2 | Add .dockerignore | 15m | Exclude target/, .git, etc. |
| T03.3 | Test container locally | 1h | Verify /actuator/health works |

**Test Strategy:** Manual — build image, run container, curl health endpoint.

**Files to Create:**
- `user-service/Dockerfile` (new)
- `user-service/.dockerignore` (new)

---

## Phase 1: Complete User Service ⬜
> **Goal:** Finish user-service MVP features  
> **Duration:** ~3 days

### Story S04: Friendship Entity & Repository ⬜
> Branch: `feature/S04-friendship-entity`

**Why:** Core social feature — users need to connect before splitting expenses.

**Acceptance Criteria:**
- [ ] Friendship entity with requester, addressee, status, timestamps
- [ ] Status enum: PENDING, ACCEPTED, REJECTED, BLOCKED
- [ ] Repository methods: findByRequesterOrAddressee, findPendingRequests
- [ ] Flyway migration creates table
- [ ] Entity tests pass

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T04.1 | Write Friendship entity test | 1h | Test-first: test status transitions |
| T04.2 | Create Friendship entity & FriendshipStatus enum | 1h | |
| T04.3 | Create FriendshipRepository with custom queries | 1h | |
| T04.4 | Add Flyway migration V5__create_friendship_table.sql | 30m | |

**Entity Design:**
```java
Friendship {
  id: Long
  requester: User (ManyToOne)
  addressee: User (ManyToOne) 
  status: FriendshipStatus (PENDING, ACCEPTED, REJECTED, BLOCKED)
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}
```

**Files to Create:**
- `user-service/src/main/java/com/splitz/user/model/Friendship.java`
- `user-service/src/main/java/com/splitz/user/model/FriendshipStatus.java`
- `user-service/src/main/java/com/splitz/user/repository/FriendshipRepository.java`
- `user-service/src/main/resources/db/migration/V5__create_friendship_table.sql`
- `user-service/src/test/java/com/splitz/user/model/FriendshipTest.java`

---

### Story S05: Friendship Service ⬜
> Branch: `feature/S05-friendship-service`

**Why:** Business logic for friend requests — validation, state transitions.

**Acceptance Criteria:**
- [ ] Send friend request (creates PENDING)
- [ ] Accept/reject friend request (updates status)
- [ ] Cannot send duplicate request
- [ ] Cannot friend yourself
- [ ] Get pending requests for user
- [ ] Get accepted friends for user
- [ ] All service methods have unit tests

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T05.1 | Write FriendshipService unit tests | 2h | Test-first: all scenarios |
| T05.2 | Implement FriendshipService | 2h | |
| T05.3 | Create FriendshipDTO and mapper | 1h | |

**Business Rules:**
- User A sends request to User B → status = PENDING
- User B accepts → status = ACCEPTED
- User B rejects → status = REJECTED
- Only addressee can accept/reject
- Either party can remove (delete) an ACCEPTED friendship

**Files to Create:**
- `user-service/src/main/java/com/splitz/user/service/FriendshipService.java`
- `user-service/src/main/java/com/splitz/user/dto/FriendshipDTO.java`
- `user-service/src/main/java/com/splitz/user/mapper/FriendshipMapper.java`
- `user-service/src/test/java/com/splitz/user/service/FriendshipServiceTest.java`

---

### Story S06: Friendship REST API ⬜
> Branch: `feature/S06-friendship-api`

**Why:** Expose friendship features to clients.

**Acceptance Criteria:**
- [ ] POST /users/{id}/friends — send friend request
- [ ] GET /users/{id}/friends — list accepted friends
- [ ] GET /users/{id}/friends/requests — list pending incoming requests  
- [ ] PUT /users/{id}/friends/{friendshipId}/accept — accept request
- [ ] PUT /users/{id}/friends/{friendshipId}/reject — reject request
- [ ] DELETE /users/{id}/friends/{friendId} — remove friend
- [ ] All endpoints require authentication
- [ ] Users can only manage their own friendships
- [ ] Integration tests pass

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T06.1 | Write FriendshipController integration tests | 2h | Test-first |
| T06.2 | Implement FriendshipController | 2h | |
| T06.3 | Add security expressions for ownership check | 30m | |
| T06.4 | Add exception handling for friendship errors | 30m | |

**Files to Create:**
- `user-service/src/main/java/com/splitz/user/controller/FriendshipController.java`
- `user-service/src/main/java/com/splitz/user/exception/FriendshipException.java`
- `user-service/src/test/java/com/splitz/user/controller/FriendshipControllerTest.java`
- `user-service/src/test/java/com/splitz/user/integration/FriendshipIntegrationTest.java`

---

### Story S07: User Service OpenAPI Documentation ⬜
> Branch: `feature/S07-openapi-docs`

**Why:** Self-documenting API for testing and future clients.

**Acceptance Criteria:**
- [ ] SpringDoc OpenAPI dependency added
- [ ] Swagger UI available at /swagger-ui.html
- [ ] All endpoints documented with descriptions
- [ ] Request/response examples included

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T07.1 | Add springdoc-openapi dependency | 30m | |
| T07.2 | Configure OpenAPI info (title, version, description) | 30m | |
| T07.3 | Annotate controllers with @Operation, @ApiResponse | 2h | |
| T07.4 | Verify Swagger UI loads correctly | 30m | Manual test |

**Files to Modify:**
- `user-service/pom.xml`
- All controllers (add annotations)
- `user-service/src/main/java/com/splitz/user/config/OpenApiConfig.java` (new)

---

## Phase 2: Common Security Module ⏸️
> **Goal:** Shared JWT validation for all services  
> **Duration:** ~1 day  
> **Status:** Deferred until expense-service needs it

**Decision:** Start expense-service with copy-paste of JWT classes. Extract common module when we have two working services — avoids premature abstraction.

### Story S08: Extract common-security Module ⏸️
> Deferred — implement when expense-service is ready for JWT

**Tasks (for later):**
- Create `common-security` Maven module
- Move JwtUtil, JwtRequestFilter, security exceptions
- Update both services to depend on common-security
- Verify both services still work

---

## Phase 3: Expense Service Foundation ⬜
> **Goal:** Bootable expense service with core entities and CRUD  
> **Duration:** ~5 days

### Story S09: Expense Service Bootstrap ⬜
> Branch: `feature/S09-expense-service-bootstrap`

**Why:** Need a running Spring Boot app before adding features.

**Acceptance Criteria:**
- [ ] Spring Boot app starts on port 8081
- [ ] H2 console available in dev mode
- [ ] Flyway runs migrations
- [ ] Actuator health endpoint responds
- [ ] JWT authentication works (copy from user-service initially)

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T09.1 | Update expense-service pom.xml with dependencies | 1h | web, security, jpa, flyway, h2, etc. |
| T09.2 | Create ExpenseServiceApplication main class | 30m | |
| T09.3 | Create application.properties (dev profile) | 30m | Port 8081, H2, Flyway |
| T09.4 | Copy JWT classes from user-service | 1h | JwtUtil, JwtRequestFilter, SecurityConfig |
| T09.5 | Create V1 Flyway migration (empty baseline) | 15m | |
| T09.6 | Verify app starts and health endpoint works | 30m | |

**Files to Create:**
- `expense-service/src/main/java/com/splitz/expense/ExpenseServiceApplication.java`
- `expense-service/src/main/resources/application.properties`
- `expense-service/src/main/resources/application-dev.properties`
- `expense-service/src/main/java/com/splitz/expense/config/SecurityConfig.java`
- `expense-service/src/main/java/com/splitz/expense/security/JwtUtil.java`
- `expense-service/src/main/java/com/splitz/expense/security/JwtRequestFilter.java`
- `expense-service/src/main/resources/db/migration/V1__baseline.sql`

---

### Story S10: Category Entity & Seeding ⬜
> Branch: `feature/S10-category-entity`

**Why:** Expenses need categories. Seed defaults so users can start immediately.

**Acceptance Criteria:**
- [ ] Category entity: id, name, icon, color, isDefault, createdAt
- [ ] Flyway migration creates table and seeds defaults
- [ ] Default categories: Food, Transport, Entertainment, Utilities, Shopping, Other
- [ ] GET /categories returns all categories
- [ ] Default categories cannot be deleted

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T10.1 | Write Category entity test | 30m | |
| T10.2 | Create Category entity and repository | 1h | |
| T10.3 | Create Flyway migration with seed data | 30m | |
| T10.4 | Create CategoryService (read-only for MVP) | 1h | |
| T10.5 | Create CategoryController with GET endpoint | 1h | |
| T10.6 | Write controller tests | 1h | |

**Files to Create:**
- `expense-service/src/main/java/com/splitz/expense/model/Category.java`
- `expense-service/src/main/java/com/splitz/expense/repository/CategoryRepository.java`
- `expense-service/src/main/java/com/splitz/expense/service/CategoryService.java`
- `expense-service/src/main/java/com/splitz/expense/controller/CategoryController.java`
- `expense-service/src/main/java/com/splitz/expense/dto/CategoryDTO.java`
- `expense-service/src/main/resources/db/migration/V2__create_category_table.sql`
- Tests for all above

---

### Story S11: Group Entity & CRUD ⬜
> Branch: `feature/S11-group-entity`

**Why:** Groups are containers for shared expenses.

**Acceptance Criteria:**
- [ ] Group entity: id, name, description, imageUrl, createdBy, isActive, timestamps
- [ ] GroupMember entity: id, groupId, userId, role (ADMIN/MEMBER), joinedAt
- [ ] Group creator automatically becomes ADMIN member
- [ ] CRUD endpoints: create, get, list (user's groups), update, soft-delete
- [ ] Only group ADMINs can update/delete group

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T11.1 | Write Group and GroupMember entity tests | 1h | |
| T11.2 | Create entities, enums, repositories | 2h | |
| T11.3 | Create Flyway migration | 30m | |
| T11.4 | Write GroupService unit tests | 2h | |
| T11.5 | Implement GroupService | 2h | |
| T11.6 | Write GroupController tests | 1h | |
| T11.7 | Implement GroupController | 1h | |

**Entity Design:**
```java
Group {
  id: Long
  name: String (required)
  description: String
  imageUrl: String
  createdBy: Long (userId)
  isActive: boolean (default true)
  createdAt, updatedAt: LocalDateTime
}

GroupMember {
  id: Long
  group: Group (ManyToOne)
  userId: Long
  role: GroupRole (ADMIN, MEMBER)
  joinedAt: LocalDateTime
}
```

**API Endpoints:**
- POST /groups — create group
- GET /groups — list user's groups
- GET /groups/{id} — get group details with members
- PUT /groups/{id} — update group (admin only)
- DELETE /groups/{id} — soft delete (admin only)
- POST /groups/{id}/members — add member (admin only)
- DELETE /groups/{id}/members/{userId} — remove member

---

### Story S12: Expense Entity & Basic CRUD ⬜
> Branch: `feature/S12-expense-entity`

**Why:** Core feature — tracking who paid what.

**Acceptance Criteria:**
- [ ] Expense entity with all fields per ERD
- [ ] Only group members can create expenses in that group
- [ ] Payer must be a group member
- [ ] CRUD: create, get, list by group, update, delete
- [ ] Only expense creator or group admin can update/delete

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T12.1 | Write Expense entity tests | 1h | |
| T12.2 | Create Expense entity and repository | 1h | |
| T12.3 | Create Flyway migration | 30m | |
| T12.4 | Write ExpenseService unit tests | 2h | |
| T12.5 | Implement ExpenseService | 2h | |
| T12.6 | Write ExpenseController tests | 1h | |
| T12.7 | Implement ExpenseController | 1h | |

**Entity Design:**
```java
Expense {
  id: Long
  group: Group (ManyToOne)
  description: String (required)
  amount: BigDecimal (required, > 0)
  currency: String (default "EUR")
  paidBy: Long (userId)
  category: Category (ManyToOne)
  expenseDate: LocalDate
  notes: String
  receiptUrl: String
  createdAt, updatedAt: LocalDateTime
}
```

---

### Story S13: Expense Splits (EQUAL & EXACT) ⬜
> Branch: `feature/S13-expense-splits`

**Why:** Core feature — tracking who owes what.

**Acceptance Criteria:**
- [ ] ExpenseSplit entity: expenseId, userId, splitType, splitValue, shareAmount
- [ ] EQUAL split: divide amount equally among specified users
- [ ] EXACT split: specify exact amount per user (must sum to total)
- [ ] Splits created when expense is created
- [ ] GET /expenses/{id} includes splits
- [ ] Validation: split amounts must equal expense amount

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T13.1 | Write split calculation tests | 2h | Test EQUAL and EXACT scenarios |
| T13.2 | Create ExpenseSplit entity | 1h | |
| T13.3 | Implement split calculation in ExpenseService | 2h | |
| T13.4 | Update expense creation to include splits | 1h | |
| T13.5 | Create Flyway migration | 30m | |

**Split Types:**
```java
// EQUAL: Split 90 among 3 people = 30 each
// EXACT: { userId1: 50, userId2: 25, userId3: 15 } = 90 total
```

---

### Story S14: Balance Calculation ⬜
> Branch: `feature/S14-balance-calculation`

**Why:** Users need to see who owes whom.

**Acceptance Criteria:**
- [ ] GET /groups/{id}/balances — returns balance per member
- [ ] Balance = amount paid - amount owed
- [ ] Positive = others owe you, Negative = you owe others
- [ ] GET /users/{id}/balances — user's balances across all groups

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T14.1 | Write balance calculation tests | 2h | Various scenarios |
| T14.2 | Implement BalanceService | 2h | |
| T14.3 | Create BalanceController | 1h | |
| T14.4 | Create BalanceDTO | 30m | |

**Example:**
```
Group: Roommates
Expense: Groceries $60, paid by Alice, split EQUAL (Alice, Bob, Carol)
→ Alice: +40 (paid 60, owes 20)
→ Bob: -20 (paid 0, owes 20)
→ Carol: -20 (paid 0, owes 20)
```

---

### Story S15: Settlement Tracking ⬜
> Branch: `feature/S15-settlements`

**Why:** Track when debts are paid off.

**Acceptance Criteria:**
- [ ] Settlement entity: groupId, payerId, payeeId, amount, status, timestamps
- [ ] Status flow: PENDING → MARKED_PAID → COMPLETED
- [ ] Payer marks as paid, Payee confirms
- [ ] Settlements reduce calculated balances
- [ ] CRUD: create, list by group, update status

**Tasks:**
| ID | Task | Est | Notes |
|----|------|-----|-------|
| T15.1 | Write Settlement entity and status tests | 1h | |
| T15.2 | Create Settlement entity and repository | 1h | |
| T15.3 | Write SettlementService tests | 2h | |
| T15.4 | Implement SettlementService | 2h | |
| T15.5 | Implement SettlementController | 1h | |
| T15.6 | Create Flyway migration | 30m | |

---

## Phase 4-7: High-Level Overview

> Detailed breakdown will be added when we reach these phases.

### Phase 4: Integration & Polish (~3 days)
- Wire user-service ↔ expense-service communication (REST client)
- End-to-end integration tests with both services
- OpenAPI documentation for expense-service
- Error handling standardization
- Performance baseline (response times)

### Phase 5: Containerization (~3 days)
- Dockerfile for expense-service
- docker-compose.yml with both services + PostgreSQL
- Environment-based configuration (.env files)
- Health checks and startup ordering
- Local development documentation

### Phase 6: Enhanced Features (~5 days)
- PERCENTAGE split type
- Recurring expenses
- User-defined categories
- Export (CSV/PDF)
- Basic analytics endpoints
- Email notification stubs

### Phase 7: Production Readiness (~5 days)
- Structured logging with correlation IDs
- Metrics (Micrometer/Prometheus)
- Rate limiting
- Security hardening (CORS, headers, input validation)
- CI/CD for Docker builds
- Deployment documentation

---

## Appendix A: Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          USER SERVICE                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐      ┌──────────────┐      ┌──────────┐              │
│  │   User   │──────│  users_roles │──────│   Role   │              │
│  └──────────┘      └──────────────┘      └──────────┘              │
│       │                                                             │
│       │ requester                                                   │
│       ▼                                                             │
│  ┌──────────────┐                                                   │
│  │  Friendship  │◄─── addressee ───┐                               │
│  └──────────────┘                  │                               │
│                                    │                               │
│                               ┌────┴─────┐                         │
│                               │   User   │                         │
│                               └──────────┘                         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        EXPENSE SERVICE                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐       ┌─────────────┐                                │
│  │ Category │       │    Group    │                                │
│  └──────────┘       └─────────────┘                                │
│       │                   │                                         │
│       │                   │                                         │
│       ▼                   ▼                                         │
│  ┌──────────┐       ┌─────────────┐      ┌────────────┐            │
│  │ Expense  │───────│ GroupMember │      │ Settlement │            │
│  └──────────┘       └─────────────┘      └────────────┘            │
│       │                                        │                    │
│       │                                        │                    │
│       ▼                                        │                    │
│  ┌──────────────┐                              │                    │
│  │ ExpenseSplit │◄─────────────────────────────┘                   │
│  └──────────────┘                                                   │
│                                                                     │
│  Note: userId references point to User Service (not FK)             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Appendix B: API Endpoints Summary

### User Service (port 8080)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | /authenticate | Public | Login |
| POST | /users | Public | Register |
| GET | /users | ADMIN | List all users |
| GET | /users/{id} | Auth | Get user |
| PUT | /users/{id} | Owner/Admin | Update user |
| DELETE | /users/{id} | Owner/Admin | Delete user |
| GET | /users/search | Auth | Search users |
| POST | /users/{id}/friends | Auth | Send friend request |
| GET | /users/{id}/friends | Auth | List friends |
| GET | /users/{id}/friends/requests | Auth | Pending requests |
| PUT | /users/{id}/friends/{fid}/accept | Auth | Accept request |
| PUT | /users/{id}/friends/{fid}/reject | Auth | Reject request |
| DELETE | /users/{id}/friends/{friendId} | Auth | Remove friend |

### Expense Service (port 8081)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | /categories | Auth | List categories |
| POST | /groups | Auth | Create group |
| GET | /groups | Auth | List user's groups |
| GET | /groups/{id} | Auth | Get group |
| PUT | /groups/{id} | Group Admin | Update group |
| DELETE | /groups/{id} | Group Admin | Delete group |
| POST | /groups/{id}/members | Group Admin | Add member |
| DELETE | /groups/{id}/members/{uid} | Group Admin | Remove member |
| POST | /groups/{id}/expenses | Member | Create expense |
| GET | /groups/{id}/expenses | Member | List expenses |
| GET | /expenses/{id} | Member | Get expense |
| PUT | /expenses/{id} | Creator/Admin | Update expense |
| DELETE | /expenses/{id} | Creator/Admin | Delete expense |
| GET | /groups/{id}/balances | Member | Group balances |
| GET | /users/{id}/balances | Auth | User balances |
| POST | /settlements | Auth | Create settlement |
| PUT | /settlements/{id}/mark-paid | Payer | Mark as paid |
| PUT | /settlements/{id}/confirm | Payee | Confirm payment |

---

## Appendix C: Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.0 |
| Security | Spring Security + JWT | |
| Database (dev) | H2 | |
| Database (prod) | PostgreSQL | 16 |
| ORM | Spring Data JPA | |
| Migrations | Flyway | |
| Build | Maven | 3.8+ |
| Mapping | MapStruct | 1.6.3 |
| Code Gen | Lombok | 1.18.x |
| API Docs | SpringDoc OpenAPI | |
| Testing | JUnit 5, Mockito, MockMvc | |

---

## Appendix D: Development Workflow

### Starting a New Story

```bash
# 1. Update main
git checkout main
git pull

# 2. Create feature branch
git checkout -b feature/S04-friendship-entity

# 3. Start with tests (test-first)
# Write failing tests for acceptance criteria

# 4. Implement until tests pass
mvn test

# 5. Verify full build
mvn clean install

# 6. Commit and push
git add .
git commit -m "S04: Add Friendship entity with tests"
git push -u origin feature/S04-friendship-entity

# 7. Create PR (optional for solo) or merge
git checkout main
git merge feature/S04-friendship-entity
git push
```

### Daily Workflow

1. Pick next ⬜ story from the roadmap
2. Update status to 🟡
3. Create branch
4. Write tests first
5. Implement
6. Run full test suite
7. Commit with story ID in message
8. Mark ✅ when merged

---

*Roadmap maintained by: Asad Soomro*  
*Last major update: December 31, 2025*

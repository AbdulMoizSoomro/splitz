# SPLITZ Implementation Roadmap

> **Last Updated:** January 4, 2026  
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

- [x] Code compiles without warnings
- [x] All new code has tests (it is important that tests are written first when possible)
- [x] All tests pass (`mvn test`)
- [x] Self-review completed
- [x] Documentation updated if public API changed

### Story Status Legend

- ⬜ Not Started
- 🟡 In Progress
- ✅ Complete
- ⏸️ Blocked/Deferred

---

## Current State Summary

| Service | Status | What Works |
|---------|--------|------------|
| user-service | 100% Phase 1 | Auth, User CRUD, Search, Roles, Flyway, Friendship API |
| expense-service | ✅ Phase 3 | Groups, Expenses, Splits, Balances, Settlements |
| common-security | 100% Phase 2 | Shared JWT, Security Config |
| DevOps | 100% Phase 0 | CI Pipeline, Test Coverage, Dockerfile |

**What's Missing for MVP:**

1. **⚠️ CRITICAL:** Phase 4.5: Critical fixes and hardening (18 days) - **NEWLY IDENTIFIED**
2. Phase 4: Inter-service communication and integration (revised, S16 reopened)
3. Phase 5: Docker Compose setup

**Architecture Audit Results (January 20, 2026):**

- ✅ Phases 0-3 validated as complete
- 🔴 5 CRITICAL production blockers identified in current code
- 🟠 6 HIGH-priority security/performance issues found
- 📋 New Phase 4.5 required before integration testing

---

## Completed: Phases 0–3 ✅

All work for Phases 0 through 3 is complete and validated (CI, test coverage, user-service, common-security, and expense-service core features). For auditability, the detailed story history is retained in the repo (commits and release notes); the roadmap now focuses on the next implementation phases.

**Completed highlights:**

- Phase 0 (DevOps): CI pipeline, JaCoCo coverage, Dockerfile for `user-service` ✅
- Phase 1 (User Service): Auth, user CRUD, friendship API, OpenAPI ✅
- Phase 2 (Common Security): `common-security` module with unit tests ✅
- Phase 3 (Expense Service Foundation): Groups, Category, Expenses, Splits, Balances, Settlements ✅

---

## ⚠️ ARCHITECTURE AUDIT FINDINGS (January 20, 2026)

An independent architecture analysis identified **5 CRITICAL** and **6 HIGH-priority** issues in the current codebase that must be addressed before MVP launch:

**Production Blockers:**

- CRITICAL-001: Blocking WebClient defeats reactive benefits (thread exhaustion)
- CRITICAL-002: No pagination on getAllUsers() (OOM risk with >10K users)
- CRITICAL-003: Generic exception handling in JWT filter (masks bugs)
- CRITICAL-004: Missing user validation in group operations (data integrity)
- CRITICAL-005: Fragile authentication context extraction (NumberFormatException risk)

**Security & Performance:**

- HIGH-001: No rate limiting (brute force vulnerability)
- HIGH-002: Missing CORS configuration (frontend integration blocked)
- HIGH-003: No circuit breaker (cascade failures)
- HIGH-004: JWT secret not externalized (version control exposure)
- HIGH-005: Eager role fetching (N+1 queries)
- HIGH-006: No token revocation (deferred to v1.0)

**Action Required:** Insert Phase 4.5 (Critical Fixes & Hardening) before current Phase 4.

See [ARCHITECTURE_ANALYSIS.md](project-analysis/ARCHITECTURE_ANALYSIS.md) for full details.

---

## Phase 4.5: Critical Fixes & Hardening (18 days) ⬜

**Goal:** Fix all critical production blockers and harden security before integration testing.

**Acceptance criteria (must pass):**

- All 5 CRITICAL issues resolved with tests
- Security hardened (CORS, rate limiting, JWT externalization)
- Resilience patterns implemented (circuit breaker, retry logic)
- Production readiness score improves from 65/100 to 85/100

### Sprint 1: Production Blockers (6-8 days) 🔴

**Story S30: Add Pagination to getAllUsers()** ⬜ (0.5 days)

- **Why:** CRITICAL-002 - Prevents OOM errors with large user bases
- **Tasks:**
  - T30.1: Modify `UserController.getAllUsers()` to return `Page<UserDTO>`
  - T30.2: Add `Pageable` parameter with default page size 20
  - T30.3: Update `UserService.getAllUsers()` signature
  - T30.4: Update tests (MockMvc with pagination assertions)
- **Acceptance Criteria:**
  - GET /users?page=0&size=20 returns paginated response
  - Response includes total elements, total pages, current page
  - Tests verify pagination edge cases (empty page, out of bounds)

**Story S37: Fix JWT Filter Exception Handling** ⬜ (1 day)

- **Why:** CRITICAL-003 - Prevents silent failures masking bugs
- **Tasks:**
  - T37.1: Replace generic `catch (Exception e)` with specific exceptions
  - T37.2: Add handlers for `ExpiredJwtException`, `MalformedJwtException`, `SignatureException`
  - T37.3: Add structured logging for each exception type
  - T37.4: Write tests for each exception scenario
- **Acceptance Criteria:**
  - Expired tokens return 401 with clear error message
  - Malformed tokens logged and return 401
  - Genuine server errors (500) not swallowed
  - All exception paths have tests

**Story S40: Add User Validation in GroupService** ⬜ (1 day)

- **Why:** CRITICAL-004 - Prevents data integrity issues
- **Tasks:**
  - T40.1: Add `UserClient.existsById(Long userId)` method
  - T40.2: Call validation in `GroupService.addMember()` before persisting
  - T40.3: Throw `ResourceNotFoundException` if user doesn't exist
  - T40.4: Add integration tests with WireMock stubbing user-service
- **Acceptance Criteria:**
  - Adding non-existent user to group returns 404
  - Valid users can be added successfully
  - Tests cover user-service timeout scenarios

**Story S39: Fix Authentication Context Extraction** ⬜ (2 days)

- **Why:** CRITICAL-005 - Prevents NumberFormatException crashes
- **Tasks:**
  - T39.1: Add `userId` claim to JWT token generation in `JwtUtil`
  - T39.2: Create custom `@AuthenticationPrincipal` annotation
  - T39.3: Create `UserPrincipal` class with userId, username, roles
  - T39.4: Update `JwtRequestFilter` to set `UserPrincipal` in SecurityContext
  - T39.5: Replace all `getName()` calls with custom principal
  - T39.6: Update all controller methods to use `@AuthenticationPrincipal UserPrincipal`
- **Acceptance Criteria:**
  - JWT contains `userId` claim (Long)
  - Controllers extract userId safely without casting
  - Tests verify principal extraction with MockMvc
  - No NumberFormatException possible

**Story S33: Fix Blocking WebClient** ⬜ (2-3 days)

- **Why:** CRITICAL-001 - Enables horizontal scaling
- **Tasks:**
  - T33.1: **DECISION POINT:** Choose Option A (RestTemplate) OR Option B (fully async)
    - **Option A:** Replace WebClient with RestTemplate (simpler, synchronous)
    - **Option B:** Make service layer fully async (Mono/Flux everywhere)
  - T33.2: Implement chosen approach
  - T33.3: Add timeout configuration (5 seconds)
  - T33.4: Update all service method signatures if Option B chosen
  - T33.5: Update tests for chosen approach
- **Acceptance Criteria:**
  - No `.block()` or `.blockOptional()` calls in production code
  - Timeout configured and tested
  - Load test shows thread pool no longer exhausted
  - All existing functionality works unchanged
- **Recommendation:** Option A (RestTemplate) for MVP speed; Option B for v1.0

### Sprint 2: Security Hardening (5-6 days) 🟠

**Story S26: CORS Configuration** ⬜ (0.5 days)

- **Why:** HIGH-002 - Enables frontend integration
- **Tasks:**
  - T26.1: Add `CorsConfiguration` bean to `SecurityConfig` in both services
  - T26.2: Configure allowed origins (environment variable)
  - T26.3: Configure allowed methods (GET, POST, PUT, DELETE, OPTIONS)
  - T26.4: Configure allowed headers (Authorization, Content-Type)
  - T26.5: Test with actual frontend or curl with Origin header
- **Acceptance Criteria:**
  - Preflight OPTIONS requests return correct headers
  - Actual requests from allowed origin succeed
  - Requests from disallowed origins rejected

**Story S27: JWT Secret Externalization** ⬜ (1 day)

- **Why:** HIGH-004 - Prevents secret exposure
- **Tasks:**
  - T27.1: Add validation in `JwtUtil` @PostConstruct
  - T27.2: Fail fast if JWT_SECRET equals default value in prod profile
  - T27.3: Update application-prod.properties documentation
  - T27.4: Update deployment README with secret generation instructions
  - T27.5: Add test verifying fail-fast behavior
- **Acceptance Criteria:**
  - Application fails to start if prod profile uses default secret
  - Clear error message guides user to set JWT_SECRET env var
  - Dev/test profiles continue to work with default

**Story S25: Rate Limiting Implementation** ⬜ (3 days)

- **Why:** HIGH-001 - Prevents brute force attacks
- **Dependencies:** Bucket4j 8.7.0
- **Tasks:**
  - T25.1: Add Bucket4j dependency to parent pom.xml
  - T25.2: Create `RateLimitingFilter` with Bucket4j
  - T25.3: Configure `/authenticate` endpoint (5 attempts/minute/IP)
  - T25.4: Configure global rate limit (100 requests/minute/user)
  - T25.5: Return 429 Too Many Requests with Retry-After header
  - T25.6: Add tests simulating rate limit exceeded
- **Acceptance Criteria:**
  - 6th authentication attempt within 1 minute returns 429
  - Rate limits reset after window expires
  - Logged-in users have higher limits than anonymous
  - Tests verify rate limiting behavior

**Story S29: Security Headers** ⬜ (2 days)

- **Why:** Defense in depth
- **Tasks:**
  - T29.1: Add security headers to SecurityConfig
  - T29.2: Configure HSTS (max-age=31536000)
  - T29.3: Configure X-Frame-Options (DENY)
  - T29.4: Configure X-Content-Type-Options (nosniff)
  - T29.5: Configure CSP (default-src 'self')
  - T29.6: Configure X-XSS-Protection (1; mode=block)
  - T29.7: Test headers with curl or browser devtools
- **Acceptance Criteria:**
  - All security headers present in responses
  - Headers enforced in production profile
  - Tests verify header presence

### Sprint 3: Resilience & Performance (7-9 days) 🟢

**Story S34: Circuit Breaker for UserClient** ⬜ (3 days)

- **Why:** HIGH-003 - Prevents cascade failures
- **Dependencies:** Resilience4j 2.1.0
- **Tasks:**
  - T34.1: Add Resilience4j dependency to expense-service
  - T34.2: Configure circuit breaker (50% failure rate, 10s wait, 5 calls minimum)
  - T34.3: Wrap `UserClient` calls with `@CircuitBreaker`
  - T34.4: Add fallback method returning cached data or error response
  - T34.5: Add Actuator endpoint for circuit breaker health
  - T34.6: Write tests simulating circuit open/closed states
- **Acceptance Criteria:**
  - Circuit opens after 50% failures in 5 calls
  - Circuit half-opens after 10 seconds
  - Fallback executes when circuit open
  - Actuator shows circuit breaker state

**Story S35: Retry Logic with Exponential Backoff** ⬜ (2 days)

- **Why:** Handles transient failures
- **Dependencies:** Resilience4j (from S34)
- **Tasks:**
  - T35.1: Configure Resilience4j retry (3 attempts, exponential backoff)
  - T35.2: Apply `@Retry` to UserClient methods
  - T35.3: Configure retry only for transient errors (5xx, timeouts)
  - T35.4: Do NOT retry on 4xx client errors
  - T35.5: Add tests with WireMock simulating transient failures
- **Acceptance Criteria:**
  - Transient failures retried 3 times with backoff
  - 404 responses not retried
  - Final failure after 3 retries propagates correctly
  - Tests verify retry behavior

**Story S31: Convert Eager Role Fetching to Lazy** ⬜ (2 days)

- **Why:** HIGH-005 - Prevents N+1 queries
- **Tasks:**
  - T31.1: Change `User.roles` to `FetchType.LAZY`
  - T31.2: Add `@EntityGraph(attributePaths = {"roles"})` to specific queries
  - T31.3: Update `UserRepository` methods needing roles
  - T31.4: Update tests to handle lazy initialization
  - T31.5: Add query count assertion tests (verify no N+1)
- **Acceptance Criteria:**
  - Roles not fetched by default
  - Specific queries fetch roles with @EntityGraph
  - No LazyInitializationException in tests or runtime
  - Query count tests pass

**Story S32: Database Connection Pool Tuning** ⬜ (1 day)

- **Why:** Performance optimization
- **Tasks:**
  - T32.1: Configure HikariCP in application.properties
  - T32.2: Set minimum-idle=5, maximum-pool-size=20
  - T32.3: Set connection-timeout=30000 (30 seconds)
  - T32.4: Set idle-timeout=600000 (10 minutes)
  - T32.5: Set max-lifetime=1800000 (30 minutes)
  - T32.6: Add query timeout (30 seconds)
  - T32.7: Add Actuator metrics for connection pool
- **Acceptance Criteria:**
  - Connection pool configured per recommendations
  - Metrics visible at /actuator/metrics/hikaricp.*
  - Load test shows improved connection handling

**Story S36: Structured Logging with Correlation IDs** ⬜ (2 days)

- **Why:** Improves debugging and observability
- **Dependencies:** Logstash-Logback 7.4
- **Tasks:**
  - T36.1: Add logstash-logback-encoder dependency
  - T36.2: Configure Logback to output JSON
  - T36.3: Create `CorrelationIdFilter` to generate/propagate IDs
  - T36.4: Add correlation ID to MDC (Mapped Diagnostic Context)
  - T36.5: Add userId to MDC after authentication
  - T36.6: Propagate correlation ID in UserClient requests (X-Correlation-ID header)
  - T36.7: Update all log statements to use SLF4J properly
- **Acceptance Criteria:**
  - Logs output in JSON format
  - Each request has unique correlation ID
  - Correlation ID propagates across service boundaries
  - User ID included in logs when authenticated
  - Tests verify correlation ID presence

**Estimated total:** 18–23 days.

---

## Phase 4: Integration & Testing (8-10 days, revised) 🟡

**Status:** IN PROGRESS (Story S16 reopened due to critical issues)  
**Dependencies:** Must complete Phase 4.5 (Critical Fixes) first  
**Goal:** Validate cross-service contracts and system behavior after fixes

**Acceptance criteria (must pass):**

- Service-to-service calls (user & expense) use a defined client and contract.
- End-to-end (E2E) tests exercise: register → create friends → create group → add members → create expense → verify balances.
- OpenAPI docs load for both services and errors follow a standardized ProblemDetail shape.
- Integration tests run in CI and fail the build if regressions are detected.

### Story S16: Inter-Service Communication (User & Expense) 🟡 (REOPENED)

**Status:** Partially complete; critical issues identified in architecture audit  
**Blocker:** Must complete S33 and S40 from Phase 4.5 first

**Why:** Expense service needs to verify user data from User Service without direct DB access.

**Completed Tasks:**

- [x] T16.1 Define `UserClient` interface in `expense-service`.
- [x] T16.2 Implement `WebClient`-based implementation.
- [x] T16.3 Add error handling for service-to-service timeouts/failures.
- [x] T16.4 JWT propagation across service boundaries.

**Issues Identified (Architecture Audit):**

- ❌ T16.2 uses `.blockOptional()` defeating reactive purpose (CRITICAL-001)
- ❌ T16.5 "User existence validation" claimed but NOT implemented (CRITICAL-004)

**NEW Tasks (Reopened):**

- [ ] T16.6 Fix blocking WebClient calls → See Story S33 in Phase 4.5
- [ ] T16.7 Actually implement user validation → See Story S40 in Phase 4.5

**Definition of ACTUALLY Done:**

- No blocking calls in WebClient implementation
- User validation called before adding group members
- Integration tests prove validation works

### Story S17: Consumer Contract Testing ⬜

**Why:** Prevent breaking changes when one service changes its API.

- T17.1 Set up Spring Cloud Contract in `user-service` (producer) and `expense-service` (consumer).
- T17.2 Define contract for user lookup and auth validation.

### Story S18: Integration Testing with Testcontainers ⬜

**Why:** Ensure services work with real Postgres and WireMock stubs.

- T18.1 Add Postgres Testcontainers to `expense-service`.
- T18.2 Add WireMock to stub `user-service` responses.

### Story S19: End-to-End (E2E) Scenarios ⬜

**Why:** Full system validation from a user's perspective.

- T19.1 Create `integration-tests` module or folder.
- T19.2 Implement "Journey 3: Creating a Group Expense" as a code-based test.

### Story S20: OpenAPI & Global Error Handling Harmonization ⬜

**Why:** Consistent API documentation and error formats across all services.

- T20.1 Align `ProblemDetail` response structure in both services.
- T20.2 Configure Swagger UI to show both service docs (if using a gateway or shared ui).

**Estimated total:** 14–25 hours.

---

## Phase 5: Containerization & Local Dev (2–4 days, detailed)

**Goal:** Provide a reproducible local environment and CI smoke tests.

**Acceptance criteria (must pass):**

- `docker-compose up` starts all services and Postgres; health endpoints return UP.
- `.env.example` documents required environment variables.
- CI smoke job runs Compose and verifies health endpoints.

### Story S21: Docker Compose Environment ⬜

**Why:** Enable developers to run the full stack with one command.

- T21.1 Create `docker-compose.yml` with `user-service`, `expense-service`, and `postgres`.
- T21.2 Add healthchecks and service dependencies.

### Story S22: Dockerfile Optimization & Image Hardening ⬜

**Why:** Secure, minimal production-ready images.

- T22.1 Optimize multi-stage builds for both services.
- T22.2 Implementation of non-root users in all containers.

### Story S23: Developer Tooling (Makefile/dotenv) ⬜

**Why:** Improve developer ergonomics and local config management.

- T23.1 Add `Makefile` for common commands (`dev-up`, `test-e2e`).
- T23.2 Add `.env.example` with default JWT secrets and DB URLs.

### Story S24: CI Smoke Testing (Sanity Check) ⬜

**Why:** Early warning if images or compose settings break.

- T24.1 Add GitHub Action job to run `docker-compose up` and curl `/actuator/health`.

**Estimated total:** 7–11 hours.

---

## Future Phases (kept short)

- Phase 6: Enhanced Features (PERCENTAGE splits, recurring, exports) — planned after integration tests pass.
- Phase 7: Production Readiness (logging, metrics, hardening) — planned for final release.

---

## ⚠️ CRITICAL DECISION REQUIRED (Story S33)

**Question 1 (BLOCKING):** For Story S33 (Fix Blocking WebClient), choose architecture:

**Option A: RestTemplate (Simpler, MVP-Recommended)**

- Pro: Simple synchronous calls, easier to reason about, faster to implement (2 days)
- Pro: No service layer signature changes (keeps `GroupDTO` return types)
- Con: Not reactive (but current code isn't truly reactive anyway due to blocking)
- **Recommendation:** Choose this for MVP speed

**Option B: Fully Async (Future-Proof)**

- Pro: True reactive architecture, better scalability at high load
- Pro: Aligns with Spring WebFlux best practices
- Con: Service layer becomes async (`Mono<GroupDTO>` everywhere) (3 days)
- Con: More complex error handling and testing
- **Recommendation:** Consider for v1.0 refactor

**Please choose Option A or B before starting Phase 4.5.**

---

## Original Clarifying Questions

1. Preferred client style for service-to-service calls: **WebClient/RestTemplate** or **OpenFeign**? (I recommend WebClient to start.)
2. Preferred consumer contract tool: **Spring Cloud Contract** or **Pact**? (Spring Cloud Contract is JVM-native and integrates well with Spring tests.)
3. Use Postgres as canonical runtime for dev and CI (recommended) — yes or no?
4. Where should E2E tests live: a dedicated `integration-tests/` module, or within `ci`/`integration` folders? (I prefer a top-level `integration-tests/` for clarity.)
5. Do you want a `Makefile` with dev commands, or only README instructions? (I can add both.)

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
| Circuit Breaker | Resilience4j | 2.1.0 | ⬜ Needed for Phase 4.5 |
| Rate Limiting | Bucket4j | 8.7.0 | ⬜ Needed for Phase 4.5 |
| Structured Logging | Logstash-Logback | 7.4 | ⬜ Needed for Phase 4.5 |
| Integration Testing | Testcontainers | 1.19.3 | ⬜ Needed for Phase 4 |
| API Mocking | WireMock | 3.3.1 | ⬜ Needed for Phase 4 |

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

*Roadmap maintained by: Abdul Moiz Soomro*  
*Last major update: January 20, 2026 (Architecture Audit - Phase 4.5 Added)*

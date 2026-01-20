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

1. Phase 4: Inter-service communication and integration
2. Phase 5: Docker Compose setup

---

## Completed: Phases 0–3 ✅

All work for Phases 0 through 3 is complete and validated (CI, test coverage, user-service, common-security, and expense-service core features). For auditability, the detailed story history is retained in the repo (commits and release notes); the roadmap now focuses on the next implementation phases.

**Completed highlights:**

- Phase 0 (DevOps): CI pipeline, JaCoCo coverage, Dockerfile for `user-service` ✅
- Phase 1 (User Service): Auth, user CRUD, friendship API, OpenAPI ✅
- Phase 2 (Common Security): `common-security` module with unit tests ✅
- Phase 3 (Expense Service Foundation): Groups, Category, Expenses, Splits, Balances, Settlements ✅

---

1. Phase 4: Integration & Polish (3–5 days, detailed) 🟡

**Goal:** Wire services together, validate cross-service contracts, and add robust end-to-end tests so the system behaves as an integrated product.

**Acceptance criteria (must pass):**

- Service-to-service calls (user & expense) use a defined client and contract.
- End-to-end (E2E) tests exercise: register → create friends → create group → add members → create expense → verify balances.
- OpenAPI docs load for both services and errors follow a standardized ProblemDetail shape.
- Integration tests run in CI and fail the build if regressions are detected.

### Story S16: Inter-service Communication (User & Expense) ✅

**Why:** Expense service needs to verify user data from the User Service without direct DB access.

- [x] T16.1 Define `UserClient` interface in `expense-service`.
- [x] T16.2 Implement `WebClient`-based implementation.
- [x] T16.3 Add error handling for service-to-service timeouts/failures.
- [x] T16.4 JWT propagation across service boundaries.
- [x] T16.5 User existence validation in `GroupService`.

### Story S17: Consumer Contract Testing

**Why:** Prevent breaking changes when one service changes its API.

- T17.1 Set up Spring Cloud Contract in `user-service` (producer) and `expense-service` (consumer).
- T17.2 Define contract for user lookup and auth validation.

### Story S18: Integration Testing with Testcontainers

**Why:** Ensure services work with real Postgres and WireMock stubs.

- T18.1 Add Postgres Testcontainers to `expense-service`.
- T18.2 Add WireMock to stub `user-service` responses.

### Story S19: End-to-End (E2E) Scenarios

**Why:** Full system validation from a user's perspective.

- T19.1 Create `integration-tests` module or folder.
- T19.2 Implement "Journey 3: Creating a Group Expense" as a code-based test.

### Story S20: OpenAPI & Global Error Handling Harmonization

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

### Story S21: Docker Compose Environment

**Why:** Enable developers to run the full stack with one command.

- T21.1 Create `docker-compose.yml` with `user-service`, `expense-service`, and `postgres`.
- T21.2 Add healthchecks and service dependencies.

### Story S22: Dockerfile Optimization & Image Hardening

**Why:** Secure, minimal production-ready images.

- T22.1 Optimize multi-stage builds for both services.
- T22.2 Implementation of non-root users in all containers.

### Story S23: Developer Tooling (Makefile/dotenv)

**Why:** Improve developer ergonomics and local config management.

- T23.1 Add `Makefile` for common commands (`dev-up`, `test-e2e`).
- T23.2 Add `.env.example` with default JWT secrets and DB URLs.

### Story S24: CI Smoke Testing (Sanity Check)

**Why:** Early warning if images or compose settings break.

- T24.1 Add GitHub Action job to run `docker-compose up` and curl `/actuator/health`.

**Estimated total:** 7–11 hours.

---

## Future Phases (kept short)

- Phase 6: Enhanced Features (PERCENTAGE splits, recurring, exports) — planned after integration tests pass.
- Phase 7: Production Readiness (logging, metrics, hardening) — planned for final release.

---

## Clarifying Questions (please answer before I implement)

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
*Last major update: December 31, 2025*

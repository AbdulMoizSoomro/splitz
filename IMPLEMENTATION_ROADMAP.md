
# SPLITZ Implementation Roadmap

**Date:** December 29, 2025  
**Goal:** Build a Splitwise-like expense splitting application for friends and roommates  
**Target:** MVP 0.0.1 → Production-ready application → Cloud deployment

---

## Architecture Recommendations (Clarified)

### 1) Service Communication
- **MVP (now):** REST over HTTP using WebClient (reactive) or RestTemplate with 3s connect/read timeouts; shared JWT validation library.
- **Post-MVP:** gRPC for synchronous calls; Kafka/RabbitMQ for events (expense-created, settlement-completed, notification). Keep REST contracts stable to ease migration.

### 2) Authentication & Gateway
- **MVP:** Each service validates JWT via shared module; no gateway.
- **v0.0.2:** Introduce Spring Cloud Gateway for centralized auth/routing/rate-limits; services stay internal.

### 3) Data & Persistence
- **Dev:** H2 in-memory per service.
- **MVP compose:** PostgreSQL 16 containers per service (separate DB per service; store only `userId` references in expense-service).
- **Migrations:** Flyway from the start; `ddl-auto=validate` in non-dev profiles.

### 4) Configuration & Secrets
- Externalize secrets (JWT, DB creds) via env vars; add `application-local.properties` for dev defaults and `.env.example` for compose.

### 5) Deployment Stages
- **Local:** Maven run per service.
- **Docker Compose (MVP):** Two app containers + two Postgres containers; healthchecks; named volumes.
- **Kubernetes (prod):** Managed Postgres, ingress, Prometheus/Grafana, centralized logging (ELK/OpenSearch). Add later.

### MVP 0.0.1 - Core Features (Unambiguous)

**User Service (8080)**
- Register/login (JWT), refresh token optional; password hashed (BCrypt); login allowed only after email verification.
- Profile: get/update self; search users by name/email (paginated, case-insensitive, partial match).
- Friends: send/accept/reject/remove; friendship active only after accept.
- Roles: seed `ROLE_USER`, `ROLE_ADMIN`; admin-only role management endpoints guarded.

- **Expense Service (8081)**
- Groups: create/update/get/list; add/remove members; member roles (ADMIN, MEMBER); auto-create a personal group per user on registration event (idempotent in expense-service) for non-group expenses; personal group not visible to others.
- Expenses: create/update/delete; list by group and by user; fields: description, amount, currency (MVP: EUR only), paidBy (userId), categoryId, expenseDate, notes, receiptUrl.
- Splits: support EQUAL and EXACT in MVP; percentage in post-MVP; store per-user shares.
- Balances: per-group and per-user aggregation; settlements are record-only in MVP (create, two-step mark-paid: payer marks paid, payee confirms; no payment gateway).
- Categories: seed defaults (Food, Transport, Entertainment, Utilities, Other).

**API Pathing (proposed)**
- User: `/api/auth/*`, `/api/users/*`, `/api/friends/*`.
- Expense: `/api/groups/*`, `/api/expenses/*`, `/api/balances/*`, `/api/settlements/*`, `/api/categories/*`.

---

## Entity Relationship Design (Concrete)

**User Service**
- `User`: id, email (unique), username (unique), password (hashed), firstName, lastName, phoneNumber (optional), enabled, verified, createdAt, updatedAt.
- `Role`: id, name (`ROLE_USER`, `ROLE_ADMIN`).
- `Friendship`: id, requesterId, addresseeId, status (`PENDING`, `ACCEPTED`, `REJECTED`, `BLOCKED` optional), createdAt.

- **Expense Service**
- `Group`: id, name, description, imageUrl (optional), createdBy (userId), createdAt, updatedAt, isActive. One implicit personal group is created per user (not shared); used for non-group expenses and created automatically on first sighting (registration event or first expense call).
- `GroupMember`: id, groupId, userId, role (`ADMIN`, `MEMBER`), joinedAt.
- `Category`: id, name, icon (optional), color (optional), isDefault (seeded records immutable), createdAt.
- `Expense`: id, groupId (required; non-group expenses use the default personal group), description, amount (BigDecimal), currency (MVP: EUR only), paidBy (userId), categoryId, expenseDate (LocalDate), notes, receiptUrl (optional), createdAt, updatedAt.
- `ExpenseSplit`: id, expenseId, userId, splitType (`EQUAL`, `EXACT`, `PERCENTAGE` post-MVP), splitValue (percentage or exact), shareAmount (computed and stored), isPaid.
- `Settlement`: id, groupId, payerId, payeeId, amount, status (`PENDING`, `MARKED_PAID_BY_PAYER`, `COMPLETED`), notes, createdAt, markedPaidAt, settledAt.

---

## Implementation Plan - Phased Approach

### Phase 1: Fix Critical Issues (Week 1, Days 1-2)

**Goals:** Unblock login, secure password storage, seed roles, finish CRUD basics.

**Tasks**
- Flip User account flags to `true`; add boolean fields if needed for future checks.
- Encode passwords on create/update using `BCryptPasswordEncoder` in service layer (do not store raw).
- Extend `UserDTO` to carry `password` and `email`; validate non-null and format.
- Add `email` (unique) to `User` entity and repository lookups.
- Seed `ROLE_USER` and `ROLE_ADMIN` via a startup `DataInitializer` (idempotent).
- Re-enable update/delete endpoints with authz guards (self or admin), null-safe partial updates.
- Add search endpoint with pagination and case-insensitive match on username/email/name fields.

**Testing/Acceptance**
- Can register and log in, receive JWT, and call a protected endpoint.
- Passwords in DB are hashed; raw password never logged.
- Roles exist after startup; default new user has `ROLE_USER`.
- Update/delete/search endpoints return expected data and honor authorization.

### Phase 2: Common Security Module (Week 1, Day 2)

**Goals:** Single JWT validation library reused by services.

**Tasks**
- New module `common-security`: JWT parsing/validation, `JwtRequestFilter`, security utils, shared exceptions.
- Publish as module dependency; wire into user-service (and later expense-service).
- Externalize JWT secret and expiration via env vars with sensible defaults.

**Testing/Acceptance**
- User-service builds and runs with shared module; existing auth flow unchanged.
- Invalid/expired tokens rejected; valid tokens accepted across services.

### Phase 3: Build Expense Service Foundation (Week 1-2, Days 3-7)

**Goals:** Bootable Spring Boot app with entities, repositories, and core CRUD for groups/expenses/splits.

**Tasks**
- Update expense-service POM: include web, security, data-jpa, validation, mapstruct, jjwt (from parent), Lombok, H2/PostgreSQL, Flyway, actuator.
- Create `ExpenseApplication` main class; add `application.properties` with H2 dev profile (port 8081) and Flyway enabled.
- Implement entities and repositories per ERD; include auditing fields; enable `@EnableJpaAuditing`.
- Implement services: `GroupService`, `ExpenseService` (split calc for EQUAL/EXACT), `SettlementService`, `CategoryService` (seed defaults).
- Controllers with DTOs and validation; consistent response codes; ProblemDetail for errors.
- Add `UserServiceClient` (WebClient) with timeouts and fallback behavior; no user data persisted, only ids.

**Testing/Acceptance**
- App starts on 8081 with H2 and Flyway baseline applied.
- Create/list groups, add members, create expenses with splits, retrieve balances endpoints stubbed or basic.
- Requests with missing/invalid JWT are rejected; valid tokens pass.

### Phase 4: Integration & Documentation (Week 2, Days 8-10)

**Goals:** End-to-end happy paths verified; APIs documented.

**Tasks**
- Wire user-service lookup from expense-service for member existence checks.
- Implement balance aggregation and settlements minimal flow (create settlement, mark complete).
- Add SpringDoc OpenAPI to both services; annotate controllers; expose `/v3/api-docs` and Swagger UI.
- Write basic integration tests for auth, user CRUD, group/expense flows; add Testcontainers for PostgreSQL optional.

**Testing/Acceptance**
- E2E: register user → login → create group → add member → create expense with EQUAL split → balances reflect shares.
- Swagger UI loads and lists endpoints for both services.

### Phase 5: Containerization (Week 2-3, Days 11-14)

**Goals:** Reproducible local stack with Docker Compose; DB migrations via Flyway.

**Tasks**
- Dockerfiles for both services (JDK 21 base, non-root user, layered jar).
- Compose file with two Postgres services, two app services, healthchecks, named volumes, env vars.
- Add `application-prod.properties` for PostgreSQL, `ddl-auto=validate`, Flyway locations.
- Provide `.env.example` with required variables (DB creds, JWT secret, ports).

**Testing/Acceptance**
- `docker compose up` starts all containers; both services healthy; can perform the E2E flow against Postgres.
- Flyway migrates schemas automatically on startup.

### Phase 6: Enhanced Features (Week 3-4, Days 15-21)

**Goals:** Broaden functionality and UX.

**Tasks**
- Add PERCENTAGE splits; recurring expenses; receipt URL handling (S3-ready abstraction, store only URL).
- Enrich categories (icons/colors) and allow user-defined categories (flagged, non-default).
- Add export endpoints (CSV/PDF stub), and analytics basics (totals by category/month).
- Email notifications via pluggable provider interface (start with log-only adapter).

**Testing/Acceptance**
- Percentage splits compute correctly; receipts accept URL; recurring creates scheduled instances or stubs.
- Analytics endpoints return aggregated data; exports produce downloadable files.

### Phase 7: Production Readiness (Week 4, Days 22-28)

**Goals:** Hardening, observability, performance.

**Tasks**
- Structured logging (JSON option), correlation IDs per request.
- Metrics (Micrometer/Prometheus), health/liveness/readiness probes.
- Rate limiting (gateway or filter), CORS config, input validation everywhere.
- Connection pooling tuning, lazy loading review, basic caching (Redis) for reference data.
- CI/CD pipeline (GitHub Actions): build, test, docker build, push, compose deploy; plan for cloud.

**Testing/Acceptance**
- Dashboards show metrics; logs include trace IDs; probes pass.
- Security scans clean; load test at target RPS within latency SLOs (define SLOs per env).
- CI runs green end-to-end.

---

## Technology Stack Summary

### Core Technologies:
- **Language**: Java 21
- **Framework**: Spring Boot 3.2.0
- **Security**: Spring Security + JWT
- **Database**: PostgreSQL (H2 for dev)
- **ORM**: Spring Data JPA / Hibernate
- **Build Tool**: Maven

### Additional Libraries:
- **Lombok**: Reduce boilerplate
- **MapStruct**: DTO mapping
- **Flyway**: Database migrations
- **SpringDoc OpenAPI**: API documentation
- **Jackson**: JSON processing
- **Hibernate Validator**: Input validation

### Future Additions:
- **gRPC**: Inter-service communication
- **Kafka/RabbitMQ**: Event-driven architecture
- **Redis**: Caching
- **Spring Cloud Gateway**: API Gateway
- **Resilience4j**: Circuit breaker
- **AWS S3**: File storage

---

## Estimated Timeline (clarified)

| Phase | Duration | Key Exit Criteria |
|-------|----------|-------------------|
| Phase 1: Fix Bugs | 2 days | Login works; passwords hashed; roles seeded; CRUD complete |
| Phase 2: Common Security | 1 day | Shared JWT module integrated; secrets externalized |
| Phase 3: Expense Foundation | 5 days | Expense service runs; core CRUD & splits working on H2; Flyway baseline |
| Phase 4: Integration & Docs | 3 days | E2E flow validated; Swagger available for both services |
| Phase 5: Containerization | 4 days | Compose up succeeds with Postgres + apps + Flyway |
| Phase 6: Enhanced Features | 7 days | Percentage splits, receipts, analytics stubs, notifications adapter |
| Phase 7: Production Readiness | 6 days | Observability, rate limits, CI/CD pipeline, perf & security checks |
| **Total** | **28 days** | **Production-ready app** |

**MVP 0.0.1 Target**: ~14 days (Phases 1-4).  
**Full Production**: ~28 days (All phases).

---

### Immediate Next Steps

1) Fix user-service blockers (account flags, password hashing, role seeding, email field, update/delete, search). 
2) Extract shared JWT module (`common-security`) and rewire user-service to use it; externalize secrets.
3) Scaffold expense-service (Boot app, POM deps, H2 config, Flyway baseline, entities/repos/services/controllers, JWT filter).
4) Add SpringDoc and minimal integration tests to prove the end-to-end flow.

If you want me to start now, I suggest **Option A:** Fix user-service blockers first, then **Option B:** extract the shared security module.

---

**Roadmap Created by:** GitHub Copilot  
**Model:** Claude Sonnet 4.5

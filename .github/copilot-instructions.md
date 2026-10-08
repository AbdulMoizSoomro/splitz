# Splitz – AI Coding Guide

> **Last Updated:** October 9, 2026
> **Project Status:** user-service, expense-service, common-security and frontend-user all implemented and tested

---

## Read This First

`CONTEXT.md` is the source of truth for domain language. Read it before changing behaviour — terms
like *Temp Friend*, *Settlement Allocation*, *Effective Opt-Out Set* and *Settled Membership Invariant*
carry precise meanings that the code depends on.

Three documents supersede anything you may remember about this codebase:

- A phased story-by-story build plan existed and has been deleted. Do not go looking for it.
- `PROJECT_ANALYSIS_REPORT.md` (Jan 2026) has been deleted. It described a two-phase skeleton with
  `expense-service` as a stub, which is no longer true.
- `docs/project-analysis/ARCHITECTURE_ANALYSIS.md` is retained for its findings, but its status
  metadata is from Jan 2026 and several findings have since been fixed. Verify against the code
  before acting on any claim in it.

---

## Quick Links

| Document | Purpose |
|----------|---------|
| [CONTEXT.md](../CONTEXT.md) | Domain language and architecture — source of truth for terminology |
| [README.md](../README.md) | Current features, API surface, stack, and how to run everything |
| [docs/adr/](../docs/adr/) | Architecture Decision Records — why the design is what it is |
| [conductor/](../conductor/) | Product definition, tech stack, and code style guides |

### Architecture Decision Records

Read the relevant ADR before touching these areas:

| ADR | Governs |
|-----|---------|
| [0001](../docs/adr/0001-shared-security-authorizer.md) | Shared stateless authorization across services |
| [0002](../docs/adr/0002-membership-lifecycle-module.md) | Group membership lifecycle and its invariants |
| [0003](../docs/adr/0003-account-level-simplification-opt-out.md) | Account-level debt-simplification opt-out |

---

## Architecture Overview

Maven multi-module, parent [pom.xml](../pom.xml).

| Module | Port | Responsibility |
|--------|------|----------------|
| `user-service` | 8080 | Users, authentication, roles, friendships; owns `user_db` |
| `expense-service` | 8081 | Groups, membership, expenses, payments, balances, debt simplification; owns `expense_db` |
| `common-security` | — | Shared library: `SharedSecurityAuthorizer`, `JwtUtil`, `JwtRequestFilter`, domain event contracts |
| `frontend-user` | 5173 | React SPA; Vite proxies `/api/user` and `/api/expense` |

Each service owns its data exclusively. `user-service` publishes domain events through a
**transactional outbox** to RabbitMQ; `expense-service` consumes them via
`UserEventListener`/`FriendshipEventListener` to maintain replicated user and friendship tables. This
keeps expense-service reads local without weakening the service boundary.

### expense-service domain seams

Business logic lives behind narrow interfaces, not in controllers or repositories. Before adding a
rule, find whether a seam already owns it:

| Seam | Owns |
|------|------|
| `governance.GroupGovernance` | Membership authority and the Settled Membership Invariant |
| `calculator.ExpenseSplitEngine` | Split maths for all five split types, remainder distribution |
| `balance.FinancialLedgerEngine` | Ledger aggregation, pushed down to the database |
| `netting.DebtNettingEngine` | Greedy net-balance matching for the Suggested Settlement Plan |
| `lifecycle.PaymentLifecycle` | `PENDING` → `MARKED_PAID` → `COMPLETED` transitions |
| `activity.ExpenseActivityLogEngine` | Field-level diffs for collaborative editing |

The Suggested Settlement Plan is **read-only**. It must never mutate expenses or splits — that is
what keeps the audit trail intact.

---

## Security

### Authentication

1. `POST /authenticate` with `{ username, password }`
2. Receive a JWT; send `Authorization: Bearer <token>` on subsequent requests
3. `JwtRequestFilter` (in `common-security`) validates and populates the `SecurityContext`

Stateless only — no sessions, no server-side token store.

### Authorization

Identity and role checks go through `SharedSecurityAuthorizer` (`common-security`), exposed to SpEL
via `@security`:

- `@security.isSelfOrAdmin(#userId)` — identity-based ownership
- `@security.isAdmin()` — role check
- `@security.isGroupMember(#groupId)` / `@security.isGroupAdmin(#groupId)` — resource-based, backed
  by `GroupGovernance`

**`isOwnerOrAdmin()` no longer exists.** Do not reintroduce it, and do not copy security expressions
between services — both services already depend on `common-security`.

Prefer `@AuthenticationPrincipal` over casting `Principal`.

### Passwords

BCrypt everywhere. Never log, return or persist a plaintext password.

---

## Key Files

| Component | Location |
|-----------|----------|
| Shared authorizer | `common-security/src/main/java/com/splitz/security/authorization/SharedSecurityAuthorizer.java` |
| JWT filter / util | `common-security/src/main/java/com/splitz/security/JwtRequestFilter.java` · `JwtUtil.java` |
| Domain event contracts | `common-security/src/main/java/com/splitz/event/` |
| Outbox publisher | `user-service/src/main/java/com/splitz/user/service/OutboxPublisher.java` |
| Event listeners | `expense-service/src/main/java/com/splitz/expense/listener/` |
| Security config (both) | `{service}/src/main/java/com/splitz/{service}/config/SecurityConfig.java` |
| OpenAPI config (both) | `{service}/src/main/java/com/splitz/{service}/config/OpenApiConfig.java` |
| Frontend features | `frontend-user/src/features/{activity,auth,balances,dashboard,expenses,groups,settings,users}` |

---

## Build & Run

```bash
# Full gate: lint, compile, test
make ready-for-ci

# Build all modules
mvn clean install

# Run a service on H2
mvn -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl expense-service spring-boot:run -Dspring-boot.run.profiles=dev

# Integrated stack (PostgreSQL + RabbitMQ + both services + frontend)
docker compose up -d --build
```

The **dev profile uses H2**, which cannot deliver cross-service events. Anything relying on
replicated user or friendship data needs the integrated stack.

---

## Configuration

| Variable | Used by | Default |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Both services | `dev` |
| `POSTGRES_URL` · `POSTGRES_USER` · `POSTGRES_PASSWORD` | Both services | — |
| `SPRING_RABBITMQ_HOST` · `SPRING_RABBITMQ_PORT` | Both services | — |
| `CORS_ALLOWED_ORIGINS` | Both services | `http://localhost` |
| `VITE_USER_SERVICE_URL` · `VITE_EXPENSE_SERVICE_URL` | Frontend | `/api/user`, `/api/expense` |

Each service has `application.properties`, `application-dev.properties` (H2) and
`application-prod.properties` (PostgreSQL), plus a committed `application.properties.example`.
Never hardcode environment-specific values in code.

---

## Database

Flyway migrations in `{service}/src/main/resources/db/migration/`:

- `user-service` — `V1`–`V6` (roles, users, users_roles, seed, friendship, outbox)
- `expense-service` — `V1`–`V21` (categories, groups, expenses, splits, settlements, activity log,
  payments unification, replicated tables, simplification, user simplification preferences)

**Never modify an applied migration. Add a new one.** `expense-service` carries a `V1__baseline.sql`
alongside its numbered migrations — leave it in place.

---

## Coding Conventions

Full guidance in [`conductor/code_styleguides/`](../conductor/code_styleguides/) — read
`general.md` before writing code. The short version:

- **Constructor injection.** No `@Autowired` on fields.
- **Lombok** for boilerplate, **MapStruct** for DTO mapping (`componentModel = "spring"`).
- **Domain logic belongs in a seam**, not in a controller or repository.
- **Document why**, not what. Keep docs in step with code.
- **Commits** are imperative and area-scoped: `fix(expense): ...`, `refactor(frontend): ...`. The body
  explains the reasoning and what was left out. Never add attribution trailers
  (`Co-Authored-By`, `Generated with`) — this history is single-author.

### Package Structure

```
com.splitz.{service}/
├── config/         # Spring configuration
├── controller/     # REST controllers
├── dto/            # DTOs
├── exception/      # Custom exceptions
├── mapper/         # MapStruct mappers
├── model/          # JPA entities
├── repository/     # Spring Data repositories
└── service/        # Business logic

# expense-service only:
├── balance/ balancesource/ calculator/ governance/ lifecycle/
├── netting/ activity/ client/ listener/ security/
```

### Error Handling

RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`. Create domain-specific exceptions rather than
throwing bare `RuntimeException`.

---

## Testing

| Suite | Command | Count |
|-------|---------|-------|
| Backend (all modules) | `mvn verify` | 492 |
| Frontend unit | `cd frontend-user && npx vitest run` | 232 |
| End-to-end | `cd frontend-user && npx playwright test --workers=1` | 41 |

When running backend tests, filter the output to keep it readable:

```bash
mvn -pl expense-service test | grep -E "Tests run: |Failures: |Errors: |BUILD SUCCESS|BUILD FAILURE"
mvn -pl expense-service test -Dtest=SomeTest
```

Patterns: `@WebMvcTest` + `@MockBean` for controllers, `@SpringBootTest` + `TestRestTemplate` for
integration, `@WithMockUser` for security.

> **End-to-end must run with `--workers=1`.** Parallel mode is harsher than CI and yields a shifting
> set of failures that pass in isolation — resource contention, not product behaviour. CI pins
> `workers: 1` with `retries: 2`.

### Continuous Integration

Four jobs in dependency order: `lint` (`mvn validate`, Checkstyle + Spotless) → `build`
(`mvn package -DskipTests`) → `test` (`mvn verify`) → `e2e` (Playwright against the Docker stack).

Workflows skip entirely for docs-only changes (`**/*.md`, `docs/**`). Anything else runs the full chain.

---

## What NOT to Break

- **Parent POM** owns dependency versions — never hardcode versions in a module.
- **Spring Boot version** is aligned via the `${spring-boot.version}` property.
- **Stateless security.** JWT only, no sessions.
- **Flyway migrations** are append-only.
- **BCrypt** for every password, without exception.
- **Ownership Invariant** — a group always has exactly one `OWNER`; transfer before leaving.
- **Settled Membership Invariant** — no leaving or removal while holding a non-zero balance or a
  pending settlement.
- **Account-level opt-out is a hard override** — a group's configuration can never weaken consent a
  user already gave (ADR 0003).
- **Simplification is read-only** — never mutate expenses or splits while computing a plan.

---

## Known Issues

- **Editing an expense loses its split type.** `GET /groups/{id}/expenses` does not return
  `splitType`, so loading an existing expense into the form falls back to `EQUAL`. Per-member
  *amounts* round-trip correctly via `splits[].shareAmount`. This is a read-path gap, not a storage
  problem — the data is persisted. Tracked in
  [#77](https://github.com/AbdulMoizSoomro/splitz/issues/77).
- **JWTs live in browser storage.** The token is persisted to `localStorage`, so any script on the
  page can read it. `HttpOnly` cookies are documented but not implemented —
  [#78](https://github.com/AbdulMoizSoomro/splitz/issues/78).

---

## Before You Open a Pull Request

1. Read `CONTEXT.md` and any relevant ADR
2. Write tests for the behaviour you intend to add, backend and frontend
3. Run the full gate:

   ```bash
   make ready-for-ci                                        # backend
   cd frontend-user && npx vitest run                       # frontend unit
   cd frontend-user && npx playwright test --workers=1      # end-to-end
   ```

4. Record any decision that changes the domain model or a service boundary as an ADR in `docs/adr/`

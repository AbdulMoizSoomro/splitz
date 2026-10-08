# SPLITZ 💸

[![CI](https://github.com/AbdulMoizSoomro/splitz/actions/workflows/ci.yml/badge.svg)](https://github.com/AbdulMoizSoomro/splitz/actions/workflows/ci.yml)

A **Splitwise-like expense splitting application** for friends, roommates and groups — a Spring Boot
microservice backend with a React single-page frontend.

Splitz goes beyond recording who owes what. It computes a **Suggested Settlement Plan** by netting
mutual debts, so a group of four can settle in two transfers instead of six, and it gives users
explicit, enforced control over whether their debts are ever simplified at all.

---

## 📖 Documentation

| Document | Purpose |
|----------|---------|
| [`CONTEXT.md`](./CONTEXT.md) | Domain language and architecture — the source of truth for terminology |
| [`docs/adr/`](./docs/adr/) | Architecture Decision Records — why the design is what it is |
| [`docs/IMPLEMENTATION_ROADMAP.md`](./docs/IMPLEMENTATION_ROADMAP.md) | Historical roadmap (retained for context; not current) |

Read `CONTEXT.md` before changing behaviour. Terms like *Temp Friend*, *Settlement Allocation* and
*Effective Opt-Out Set* have precise meanings that the code depends on.

---

## 🚀 Features

### Identity & social graph
- Registration and JWT authentication (BCrypt hashing, stateless bearer tokens)
- Roles (`ADMIN`, `USER`) with method-level security
- User search by username, email or name, plus bulk lookup
- Friend requests: send, accept, reject, cancel; remove friend
- User profiles and activity history

### Groups & membership
- Group creation, update and deletion
- Membership lifecycle with role management (`OWNER` / `ADMIN` / `MEMBER`)
- Governance: only an `OWNER` can demote an `ADMIN`; `ADMIN`s hold peer-removal authority; the owner
  is untouchable
- **Settled Membership Invariant** — a user cannot leave while holding a non-zero balance or a pending
  settlement

### Expenses
- Five split types: `EQUAL`, `EXACT`, `PERCENTAGE`, `SHARES`, `ADJUSTMENT`
- Currency-aware precision handling with remainder distribution
- Collaborative editing with an activity log capturing field-level diffs
- Categories and bulk fetch

### Payments & balances
- Two canonical payment types: **group** and **direct** (friend-to-friend)
- Full lifecycle: `PENDING` → `MARKED_PAID` → `COMPLETED`
- Automatic multi-group settlement allocation
- Ledger aggregation pushed down to the database
- Unified activity stream across groups and friendships

### Smart Debt Reduction
- **Greedy net-balance matching** (O(N log N)) guaranteeing at most N−1 transactions for N participants
- Read-only **Suggested Settlement Plan** — never mutates expenses or splits, so the audit trail stays intact
- Two scopes: intra-group, or global cross-group aggregation
- Group governance toggle (Admin/Owner)
- **Dual-level opt-out** — per group, and an account-level opt-out that acts as a hard override across
  every group (see [ADR 0003](./docs/adr/0003-account-level-simplification-opt-out.md))

---

## 🛠️ Technology Stack

### Backend

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.12 |
| Security | Spring Security + JWT (jjwt), shared `common-security` library |
| Database | PostgreSQL (H2 for unit tests) |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Mapping | MapStruct |
| Messaging | RabbitMQ — transactional outbox for cross-service event delivery |
| Build | Maven |

### Frontend

| Layer | Technology |
|-------|------------|
| Framework | React 19 |
| Language | TypeScript 6 |
| Build | Vite 8 |
| Styling | Tailwind CSS 4 + shadcn/ui (Base UI) |
| Server state | TanStack Query 5 |
| Client state | Zustand 5 |
| Routing | React Router 7 |
| Tests | Vitest 4 (unit) · Playwright 1.59 (end-to-end) |

---

## 📁 Project Structure

```
splitz/
├── pom.xml                        # Parent POM
├── user-service/                  # Identity, profiles, friendships, roles  (:8080)
├── expense-service/               # Groups, expenses, payments, balances, netting  (:8081)
├── common-security/               # Shared security library (authorizer, JWT, filters)
├── frontend-user/                 # React SPA
├── conductor/                     # Agent tooling and product guidelines
├── config/init-db/                # Database bootstrap for the integrated environment
├── docs/                          # ADRs, roadmaps, diagrams
├── docker-compose.yml             # Integrated environment
└── CONTEXT.md                     # Domain language (source of truth)
```

### Services

| Service | Port | Responsibility |
|---------|------|----------------|
| `user-service` | 8080 | Users, authentication, roles, friendships |
| `expense-service` | 8081 | Groups, membership, expenses, payments, balances, debt simplification |

They are separate databases with separate schemas. Cross-service consistency is maintained by
publishing domain events through a transactional outbox to RabbitMQ, which `expense-service` consumes
to replicate the user and friendship data it needs.

---

## 🏃 Getting Started

### Prerequisites

- **Java 21**
- **Maven 3.8+**
- **Node.js 22**
- **Docker** + Docker Compose — required for the integrated environment and the end-to-end tests

### Option 1 — Integrated environment (recommended)

Brings up PostgreSQL, RabbitMQ, both services and the frontend. This is the only configuration the
end-to-end suite exercises, because the tests depend on genuine cross-service event delivery that H2
cannot provide.

```bash
git clone https://github.com/AbdulMoizSoomro/splitz.git
cd splitz

docker compose up -d --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| User service | http://localhost:8080 |
| Expense service | http://localhost:8081 |
| RabbitMQ management | http://localhost:15672 (`guest` / `guest`) |

The Vite dev server proxies `/api/user` and `/api/expense` to the two backends, so the frontend needs
no CORS configuration.

```bash
docker compose down -v      # stop and discard the database volume
```

### Option 2 — Running services individually (H2)

Fast for backend work. Cross-service features that rely on replicated data will not function.

```bash
mvn -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl expense-service spring-boot:run -Dspring-boot.run.profiles=dev

cd frontend-user && npm ci && npm run dev
```

---

## 🧪 Testing

| Suite | Command | Count |
|-------|---------|-------|
| Backend (all modules) | `mvn verify` | 492 tests |
| Frontend unit | `cd frontend-user && npx vitest run` | 232 tests |
| End-to-end | `cd frontend-user && npx playwright test` | 41 tests |

```bash
# Backend, single module
mvn -pl expense-service test

# Backend with coverage
mvn verify
# reports land in */target/site/jacoco/

# Frontend, single file
cd frontend-user && npx vitest run src/features/balances

# End-to-end — use workers=1, matching CI
cd frontend-user && npx playwright test --workers=1
```

### ⚠️ A note on running the end-to-end suite

Run it with **`--workers=1`**. The default parallel mode is harsher than CI and produces a shifting
handful of failures — a different subset on each run — that all pass in isolation. This is resource
contention, not product behaviour: CI pins `workers: 1` with `retries: 2` for exactly this reason.

The suite requires the integrated Docker stack to be running.

### Continuous integration

Four jobs, in dependency order:

| Job | Command | Purpose |
|-----|---------|---------|
| `lint` | `mvn validate` | Checkstyle + Spotless |
| `build` | `mvn package -DskipTests` | Compile all modules |
| `test` | `mvn verify` | Backend suite with JaCoCo coverage |
| `e2e` | Playwright | Full stack in Docker, then the end-to-end suite |

---

## 🔑 API

Base URLs: `http://localhost:8080` (user) · `http://localhost:8081` (expense).

All endpoints except registration and login require `Authorization: Bearer <token>`.

### Authentication & users — `user-service`

| Method | Endpoint | Auth |
|--------|----------|------|
| POST | `/authenticate` | Public |
| POST | `/users` | Public |
| GET | `/users/me` | Self |
| GET | `/users/{id}` | Authenticated |
| GET | `/users` · `/users/bulk` | Authenticated |
| GET | `/users/search?query=` | Authenticated |
| PUT · DELETE | `/users/{id}` | Owner / ADMIN |
| GET · POST | `/roles` · `/roles/id/{id}` · `/roles/search` | Varies |

### Friendships — `user-service`

| Method | Endpoint | Auth |
|--------|----------|------|
| GET · POST | `/users/{userId}/friends` | Authenticated |
| GET | `/users/{userId}/friends/requests` | Self |
| PUT | `/users/{userId}/friends/{id}/accept` · `/reject` | Recipient |
| DELETE | `/users/{userId}/friends/{friendId}` | Authenticated |

### Groups & membership — `expense-service`

| Method | Endpoint | Auth |
|--------|----------|------|
| GET · POST | `/groups` | Authenticated / ADMIN |
| GET · PUT · DELETE | `/groups/{groupId}` | Member / Admin |
| GET | `/groups/{groupId}/activity` | Member |
| POST | `/groups/{groupId}/members` · `/members/bulk` | Admin (per governance) |
| GET | `/groups/{groupId}/potential-members` | Member |
| PUT | `/groups/{groupId}/members/{userId}/role` | Owner / ADMIN |
| DELETE | `/groups/{groupId}/members/{userId}` | Self / Admin |

### Expenses — `expense-service`

| Method | Endpoint | Auth |
|--------|----------|------|
| POST | `/groups/{groupId}/expenses` | Member |
| GET | `/groups/{groupId}/expenses` · `/groups/expenses/bulk` | Member |
| PUT · DELETE | `/expenses/{id}` | Per collaborative-editing policy |
| GET · PUT · DELETE | `/expenses/{id}` | Member |
| GET · POST · PUT · DELETE | `/categories` · `/categories/{id}` | Member / Admin |

### Payments & balances — `expense-service`

| Method | Endpoint | Auth |
|--------|----------|------|
| POST | `/settlements` · `/payments` | Authenticated |
| POST | `/groups/{groupId}/payments` | Group member |
| POST | `/payments/direct` | Authenticated |
| GET | `/settlements/{id}` · `/groups/{groupId}/settlements` · `/users/{a}/friendships/{b}/settlements` | Party to the payment |
| PUT | `/settlements/{id}` | Payer |
| PUT | `/settlements/{id}/mark-paid` · `/confirm` | Payer / payee |
| GET | `/groups/{id}/balances` · `/users/{id}/balances` | Member / self |
| GET | `/users/{userId}/balances/with/{friendId}` · `/users/{id}/counterparties` | Self |
| GET | `/activity` | Authenticated |

> `/payments` and `/settlements` are interchangeable aliases. Both are live; `/payments` is canonical.

### Debt simplification — `expense-service`

| Method | Endpoint | Auth |
|--------|----------|------|
| GET | `/groups/{groupId}/simplification-plan` | Group member |
| GET | `/groups/{groupId}/simplification-settings` | Group member |
| PUT | `/groups/{groupId}/simplification-settings` | Group **admin** |
| POST | `/groups/{groupId}/simplification-settings/opt-out` | Group member |
| GET | `/simplification-preferences/me` | Authenticated |
| POST | `/simplification-preferences/me/opt-out` | Authenticated |

The last two are deliberately scoped to the caller with **no group in the path and no admin gate**. An
account-level opt-out is a consent decision about one's own debts, so no role — not even a group owner
— can read or change another user's preference.

### Example

```bash
# Register
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"john_doe","email":"john@example.com",
       "password":"securePassword123","firstName":"John","lastName":"Doe"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" \
  -d '{"username":"john_doe","password":"securePassword123"}' | jq -r .token)

# Authenticated request
curl http://localhost:8080/users/me -H "Authorization: Bearer $TOKEN"
```

---

## ⚙️ Configuration

| Variable | Used by | Default |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Both services | `dev` |
| `POSTGRES_URL` | Both services | — |
| `POSTGRES_USER` · `POSTGRES_PASSWORD` | Both services | — |
| `SPRING_RABBITMQ_HOST` · `SPRING_RABBITMQ_PORT` | Both services | — |
| `CORS_ALLOWED_ORIGINS` | Both services | `http://localhost` |
| `VITE_USER_SERVICE_URL` · `VITE_EXPENSE_SERVICE_URL` | Frontend | `/api/user`, `/api/expense` |

See `docker-compose.yml` for the integrated values.

---

## 🏗️ Architecture

```
                    ┌──────────────────────────┐
                    │   frontend-user  :5173   │
                    │   React SPA (Vite)        │
                    └───────┬─────────┬────────┘
                    /api/user│         │/api/expense
                ┌───────────▼──┐   ┌──▼───────────────┐
                │ user-service │   │ expense-service  │
                │    :8080     │   │     :8081        │
                └───┬───────┬───┘   └───┬─────────┬────┘
                    │       │           │         │
          ┌─────────▼─┐  ┌──▼───────────▼──┐  ┌───▼─────────┐
          │ user_db   │  │   expense_db    │  │  RabbitMQ   │
          │(PostgreSQL)│  │  (PostgreSQL)   │──│ (outbox bus) │
          └───────────┘  └─────────────────┘  └─────────────┘
```

Each service owns its data exclusively. `user-service` publishes domain events through a transactional
outbox; `expense-service` consumes them to maintain replicated user and friendship tables, which keeps
its reads local without weakening service boundaries.

---

## ⚠️ Known issues

- **`npm run build` passes, but editing an expense loses its original split type.** `GET /groups/{id}/expenses`
  does not return `splitType` — the field exists only on the create and update requests — and
  `ExpenseSplit` carries no type information either. So when an existing expense is loaded into the
  form, the split type falls back to `EQUAL`, even if it was created as `EXACT` or `PERCENTAGE`. Per-member
  *amounts* still round-trip because they come from `splits[].shareAmount`. The correct fix is to add
  `splitType` to `ExpenseDTO` and type it on the client `Expense`.
- **JWTs are held in browser storage.** The token is persisted to `localStorage`, so it is readable by
  any script running on the page. Moving to `HttpOnly` cookies is documented but not implemented — see
  [`docs/future-improvements/secure-auth-implementation.md`](./docs/future-improvements/secure-auth-implementation.md).
- **The `LICENSE` file is absent.** The project states MIT, but no licence text is committed.
- **The frontend bundle is a single ~800 kB chunk.** Vite warns about this at build time. Code-splitting
  by route has not been done.

---

## 🤝 Contributing

1. Read `CONTEXT.md` and any relevant ADR first
2. Create a feature branch (`git checkout -b feature/<name>`)
3. Write tests for the behaviour you intend to add — backend and front-end
4. Make sure the full gate passes before pushing:
   ```bash
   mvn verify                                  # backend
   cd frontend-user && npx vitest run           # front-end unit
   cd frontend-user && npx playwright test --workers=1   # end-to-end
   ```
5. Push and open a pull request

Record any decision that changes the domain model or a service boundary as an ADR in `docs/adr/`.

---

## 📝 License

MIT. Note: no `LICENSE` file is present in the repository yet, so this is a stated intent rather than
a committed licence text.

---

## 📬 Contact

Maintained by **Abdul Moiz Soomro**
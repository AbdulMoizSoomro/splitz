# Splitz – AI Coding Guide

> **Last Updated:** December 31, 2025  
> **Project Status:** User Service ~80% complete, Expense Service not started

---

## Quick Links

- [IMPLEMENTATION_ROADMAP.md](../docs/IMPLEMENTATION_ROADMAP.md) — Stories, tasks, and development workflow
- [MVP_0.0.1.md](../docs/MVP_0.0.1.md) — MVP scope, API contracts, data models
- [PROJECT_ANALYSIS_REPORT.md](../PROJECT_ANALYSIS_REPORT.md) — Architecture analysis

---

## Architecture Overview

- **Structure**: Maven multi-module with parent [pom.xml](../pom.xml)
- **Services**: `user-service` (Spring Boot 3.2, Java 21) and `expense-service` (stub only)
- **Target**: Splitwise-like expense splitting for friends/roommates

---

## User Service (Port 8080)

### What Works ✅

- JWT authentication via `/authenticate`
- User CRUD: create, read, update, delete
- User search with pagination (`/users/search?query=`)
- Role-based access control (ROLE_USER, ROLE_ADMIN)
- Password encoding (BCrypt)
- Flyway migrations for schema management
- Method-level security with `@PreAuthorize`

### Key Files

| Component            | Location                                                                           |
| -------------------- | ---------------------------------------------------------------------------------- |
| Security Config      | `user-service/src/main/java/com/splitz/user/config/SecurityConfig.java`            |
| JWT Utilities        | `user-service/src/main/java/com/splitz/user/security/JwtUtil.java`                 |
| Auth Controller      | `user-service/src/main/java/com/splitz/user/security/AuthController.java`          |
| User Controller      | `user-service/src/main/java/com/splitz/user/controller/UserController.java`        |
| User Entity          | `user-service/src/main/java/com/splitz/user/model/User.java`                       |
| User Mapper          | `user-service/src/main/java/com/splitz/user/mapper/UserMapper.java`                |
| Exception Handler    | `user-service/src/main/java/com/splitz/user/exception/GlobalExceptionHandler.java` |
| Security Expressions | `user-service/src/main/java/com/splitz/user/security/SecurityExpressions.java`     |
| Flyway Migrations    | `user-service/src/main/resources/db/migration/`                                    |

### API Endpoints

| Method | Endpoint        | Auth          | Description    |
| ------ | --------------- | ------------- | -------------- |
| POST   | `/authenticate` | Public        | Login, get JWT |
| POST   | `/users`        | Public        | Register       |
| GET    | `/users`        | ADMIN         | List all users |
| GET    | `/users/{id}`   | Authenticated | Get user by ID |
| PUT    | `/users/{id}`   | Owner/Admin   | Update user    |
| DELETE | `/users/{id}`   | Owner/Admin   | Delete user    |
| GET    | `/users/search` | Authenticated | Search users   |

### Not Yet Implemented ⬜

- Friendship API (send/accept/reject friend requests)
- OpenAPI/Swagger documentation

---

## Expense Service (Port 8081)

### Current State

- **Stub only** — contains placeholder `Main.java`
- Needs full Spring Boot scaffold before adding features

### When Building Expense Service

1. Update `expense-service/pom.xml` with dependencies (web, security, jpa, flyway, h2, mapstruct, lombok)
2. Create `ExpenseServiceApplication.java` main class
3. Add `application.properties` (port 8081, H2 dev, Flyway enabled)
4. Copy JWT classes from user-service (JwtUtil, JwtRequestFilter, SecurityConfig)
5. Create Flyway baseline migration
6. See [MVP_0.0.1.md](../docs/MVP_0.0.1.md) for entity designs and API contracts

### Planned Entities

- Group, GroupMember, Category, Expense, ExpenseSplit, Settlement
- Store `userId` references only — call user-service for user details

---

## Build & Run

```bash
# Build all modules
mvn clean install

# Run user service (dev mode with H2)
mvn -pl user-service spring-boot:run

# Run tests
mvn -pl user-service test

# Run expense service (once scaffolded)
mvn -pl expense-service spring-boot:run
```

---

## Configuration

### Profiles

- `dev` — H2 in-memory, Flyway enabled, debug logging
- `prod` — PostgreSQL, Flyway validate mode

### Environment Variables

| Variable                 | Description                | Default                 |
| ------------------------ | -------------------------- | ----------------------- |
| `JWT_SECRET`             | Base64-encoded signing key | (dev key in properties) |
| `JWT_EXPIRATION`         | Token TTL in milliseconds  | 86400000 (24h)          |
| `SPRING_PROFILES_ACTIVE` | Active profile             | dev                     |

### Key Config Files

- `user-service/src/main/resources/application.properties` — main config
- `user-service/src/main/resources/application-dev.properties` — H2 settings
- `user-service/src/main/resources/application-prod.properties` — PostgreSQL settings

---

## Database

### Flyway Migrations

Migrations are in `src/main/resources/db/migration/`:

- `V1__create_roles_table.sql`
- `V2__create_users_table.sql`
- `V3__create_users_roles_table.sql`
- `V4__seed_roles_and_admin.sql`

### Schema Notes

- Roles seeded on startup: `ROLE_USER`, `ROLE_ADMIN`
- Test admin user seeded (check V4 migration for credentials)
- H2 console available at `/h2-console` in dev mode

---

## Security

### Authentication Flow

1. POST `/authenticate` with `{ username, password }`
2. Receive JWT token in response
3. Include `Authorization: Bearer <token>` header on subsequent requests
4. JwtRequestFilter validates token and sets SecurityContext

### Authorization

- `@PreAuthorize("permitAll()")` — public endpoints
- `@PreAuthorize("isAuthenticated()")` — any logged-in user
- `@PreAuthorize("hasRole('ADMIN')")` — admin only
- `@PreAuthorize("@security.isOwnerOrAdmin(#id)")` — owner or admin check

### Security Expressions

Custom expressions in `SecurityExpressions.java`:

- `isOwnerOrAdmin(userId)` — checks if current user owns resource or is admin

---

## Coding Conventions

### General

- Use **constructor injection** (avoid `@Autowired` on fields)
- Use **Lombok** for boilerplate (`@Getter`, `@Setter`, `@AllArgsConstructor`)
- Use **MapStruct** for DTO mapping (`componentModel = "spring"`)
- Follow **test-first** approach when possible

### Package Structure

```
com.splitz.{service}/
├── config/         # Spring configuration
├── controller/     # REST controllers
├── dto/            # Data transfer objects
├── exception/      # Custom exceptions
├── mapper/         # MapStruct mappers
├── model/          # JPA entities
├── repository/     # Spring Data repositories
├── security/       # JWT, filters, auth
└── service/        # Business logic
```

### Error Handling

- Use RFC 7807 `ProblemDetail` for error responses
- Add handlers to `GlobalExceptionHandler`
- Create domain-specific exceptions (e.g., `UserAlreadyExistsException`)

---

## Testing

### Test Structure

- Unit tests: `src/test/java/.../service/`, `src/test/java/.../controller/`
- Integration tests: `src/test/java/.../integration/`
- Test config: `src/test/resources/application-test.properties`

### Running Tests

```bash
# All tests
mvn -pl user-service test

# Specific test class
mvn -pl user-service test -Dtest=UserControllerTest

# With coverage (once JaCoCo configured)
mvn -pl user-service test jacoco:report
```

### Test Patterns

- Use `@WebMvcTest` + `@MockBean` for controller unit tests
- Use `@SpringBootTest` + `TestRestTemplate` for integration tests
- Use `@WithMockUser` for security testing

---

## What NOT to Break

- **Parent POM** manages all dependency versions — don't hardcode versions in modules
- **Spring Boot version** aligned via `${spring-boot.version}` property
- **Stateless security** — no sessions, JWT only
- **Flyway migrations** — never modify existing migrations, add new ones
- **BCrypt encoding** — passwords must always be hashed before storage

---

## Next Steps (from Roadmap)

1. **S01**: GitHub Actions CI pipeline
2. **S02**: JaCoCo test coverage
3. **S03**: Docker setup for user-service
4. **S04-S06**: Friendship API (entity, service, controller)
5. **S07**: OpenAPI documentation
6. **S09+**: Expense service scaffold and features

See [IMPLEMENTATION_ROADMAP.md](../IMPLEMENTATION_ROADMAP.md) for full story breakdown.

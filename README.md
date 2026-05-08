# SPLITZ 💸

[![CI](https://github.com/AbdulMoizSoomro/splitz/actions/workflows/ci.yml/badge.svg)](https://github.com/AbdulMoizSoomro/splitz/actions/workflows/ci.yml)

A **Splitwise-like expense splitting application** for friends and roommates, built with microservices architecture using Spring Boot 3.2 and Java 21.

---

## 🚀 Features

### Current (MVP 0.0.1)

- **User Management**: Registration, authentication, profile management
- **JWT Authentication**: Stateless, secure token-based auth with BCrypt password hashing
- **Role-Based Access Control**: ADMIN and USER roles with method-level security
- **User Search**: Paginated search by username, email, or name
- **Friend Management**: Send, accept, reject friend requests

### Planned

- **Expense Tracking**: Create, update, delete expenses with multiple split types
- **Groups**: Manage expense groups with members
- **Settlements**: Track and settle balances between users
- **Analytics**: Expense reports and insights
- **Multi-currency Support**: Handle expenses in different currencies

---

## 🛠️ Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Security | Spring Security + JWT |
| Database | PostgreSQL (H2 for dev) |
| ORM | Spring Data JPA / Hibernate |
| Build | Maven |
| Mapping | MapStruct |
| Migrations | Flyway |
| Code Generation | Lombok |

---

## 📁 Project Structure

```
splitz/
├── pom.xml                          # Parent POM with dependency management
├── user-service/                    # User & authentication microservice
│   ├── src/main/java/com/splitz/user/
│   │   ├── config/                  # Security & app configuration
│   │   ├── controller/              # REST controllers
│   │   ├── dto/                     # Data transfer objects
│   │   ├── exception/               # Custom exceptions & handlers
│   │   ├── mapper/                  # MapStruct mappers
│   │   ├── model/                   # JPA entities
│   │   ├── repository/              # Spring Data repositories
│   │   ├── security/                # JWT & auth components
│   │   └── service/                 # Business logic
│   └── src/main/resources/
│       ├── application.properties   # Main config
│       ├── application-dev.properties
│       ├── application-prod.properties
│       └── db/migration/            # Flyway migrations
├── expense-service/                 # Expense management (in development)
├── IMPLEMENTATION_ROADMAP.md        # Detailed implementation plan
└── PROJECT_ANALYSIS_REPORT.md       # Architecture & analysis docs
```

---

## 🏃 Getting Started

### Prerequisites

- **Java 21** or later
- **Maven 3.8+**
- **PostgreSQL** (optional, H2 used for development)

### Build

```bash
# Clone the repository
git clone https://github.com/yourusername/splitz.git
cd splitz

# Build all modules
mvn clean install
```

### Run User Service

```bash
# Development mode (H2 in-memory database)
mvn -pl user-service spring-boot:run

# Or with specific profile
mvn -pl user-service spring-boot:run -Dspring-boot.run.profiles=dev
```

The service starts at `http://localhost:8080`

### Run Tests

```bash
# Run all tests
mvn -pl user-service test

# Run specific test class
mvn -pl user-service test -Dtest=UserControllerTest
```

---

## 🔑 API Endpoints

### Authentication

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/authenticate` | Login and get JWT token | Public |
| POST | `/users` | Register new user | Public |

### Users

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/users` | Get all users | ADMIN |
| GET | `/users/{id}` | Get user by ID | Authenticated |
| PUT | `/users/{id}` | Update user | Owner/ADMIN |
| DELETE | `/users/{id}` | Delete user | Owner/ADMIN |
| GET | `/users/search?query=` | Search users | Authenticated |

### Example Requests

**Register a new user:**

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "password": "securePassword123",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

**Login:**

```bash
curl -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "password": "securePassword123"
  }'
```

**Access protected endpoint:**

```bash
curl http://localhost:8080/users/1 \
  -H "Authorization: Bearer <your-jwt-token>"
```

---

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Base64-encoded JWT signing key | (dev key) |
| `JWT_EXPIRATION` | Token expiration in ms | 86400000 (24h) |
| `SPRING_PROFILES_ACTIVE` | Active profile (dev/prod) | dev |

### Database Configuration

**Development (H2):**

```properties
spring.datasource.url=jdbc:h2:mem:splitzdb
spring.jpa.hibernate.ddl-auto=create-drop
```

**Production (PostgreSQL):**

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/splitz
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
```

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌─────────────────┐
│   API Gateway   │     │  (Future: K8s   │
│   (Future)      │     │   Ingress)      │
└────────┬────────┘     └─────────────────┘
         │
    ┌────┴────┐
    │   JWT   │
    │  Auth   │
    └────┬────┘
         │
┌────────┴────────┬─────────────────┐
│                 │                 │
▼                 ▼                 ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│  User    │  │ Expense  │  │ Notifi-  │
│ Service  │  │ Service  │  │ cation   │
│  :8080   │  │  :8081   │  │ (Future) │
└────┬─────┘  └────┬─────┘  └──────────┘
     │             │
     ▼             ▼
┌──────────┐  ┌──────────┐
│PostgreSQL│  │PostgreSQL│
│  (users) │  │(expenses)│
└──────────┘  └──────────┘
```

---

## 📊 Development Status

| Service | Status | Progress |
|---------|--------|----------|
| User Service | ✅ Active | ~80% |
| Expense Service | 🚧 In Progress | ~10% |
| API Gateway | 📋 Planned | 0% |
| Notifications | 📋 Planned | 0% |

See [./docs/IMPLEMENTATION_ROADMAP.md](./docs/IMPLEMENTATION_ROADMAP.md) for detailed plans. These plans are updated regularly with each development implemenationtation.

---

## 🧪 Testing

The project includes:

- **Unit Tests**: Service and controller layer tests with Mockito
- **Integration Tests**: Full API flow tests with Spring Boot Test
- **Security Tests**: JWT validation and authorization tests

```bash
# Run with coverage
mvn -pl user-service test jacoco:report
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 📬 Contact

Project maintained by **Abdul Moiz Soomro**

---

*Built with ❤️ using Spring Boot*

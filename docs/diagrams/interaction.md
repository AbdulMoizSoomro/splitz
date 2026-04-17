# Service Interaction & Security Diagram

This diagram illustrates the authentication flow and how the JWT is propagated between services.

```mermaid
sequenceDiagram
    autonumber
    
    participant User as Splitz User
    participant US as User Service
    participant ES as Expense Service
    participant DB_US as User DB
    participant DB_ES as Expense DB

    Note over User, US: [ Authentication Flow ]
    User->>+US: POST /auth/login (credentials)
    US->>+DB_US: Validate credentials
    DB_US-->>-US: User data & roles
    US-->>-User: 200 OK + JWT Token

    Note over User, ES: [ Authorized Request Flow ]
    User->>+ES: POST /groups/{id}/expenses (JWT in Header)
    Note right of ES: JwtRequestFilter validates JWT
    
    Note over ES, US: [ Inter-service Communication ]
    ES->>+US: GET /users/{id} (JWT Propagated)
    US->>+DB_US: Fetch user details
    DB_US-->>-US: User info
    US-->>-ES: 200 OK (User Data)

    ES->>+DB_ES: Save Expense & Splits
    DB_ES-->>-ES: Success
    ES-->>-User: 201 Created
```

## Security Mechanism

- **JWT (JSON Web Token)**: Used for stateless authentication across the system.
- **Common Security Module**: Both services use the `common-security` library, which provides:
  - **JwtUtil**: For token generation (User Service) and validation (both).
  - **JwtRequestFilter**: A Spring Security filter that extracts and validates the JWT from the `Authorization` header.
- **Token Propagation**: When the Expense Service needs to call the User Service, it uses a `WebClient` configured with an `ExchangeFilterFunction` to automatically attach the current user's JWT to the outgoing request.

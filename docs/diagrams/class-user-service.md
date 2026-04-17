# Class Diagram: User Service

This diagram provides a detailed view of the class structures and relationships within the `user-service`.

```mermaid
classDiagram
    %% Entities
    class User {
        -Long id
        -String username
        -String email
        -String firstName
        -String lastName
        -String password
        -boolean enabled
        -boolean verified
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        -Set~Role~ roles
        +getAuthorities() Collection
    }

    class Role {
        -Long id
        -String name
    }

    class Friendship {
        -Long id
        -User requester
        -User addressee
        -FriendshipStatus status
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        +accept()
        +reject()
        +block()
    }

    %% Enums
    class FriendshipStatus {
        <<enumeration>>
        PENDING
        ACCEPTED
        REJECTED
        BLOCKED
    }

    %% Repositories
    class UserRepository {
        <<interface>>
        +findByusername(String username) Optional
        +findByEmail(String email) Optional
        +searchByUsernameOrEmailOrFirstName(String query, Pageable pageable) Page
    }

    class RoleRepository {
        <<interface>>
        +findByName(String name) Optional
    }

    class FriendshipRepository {
        <<interface>>
        +findByRequesterIdAndStatus(Long requesterId, FriendshipStatus status) List
        +findByAddresseeIdAndStatus(Long addresseeId, FriendshipStatus status) List
        +findFriendshipBetween(Long userId1, Long userId2) Optional
    }

    %% Services
    class UserService {
        -UserRepository userRepository
        -RoleRepository roleRepository
        -UserMapper userMapper
        -BCryptPasswordEncoder passwordEncoder
        +createUser(UserDTO newUserDTO) UserDTO
        +getUserbyId(long id) Optional
        +updateUser(Long id, UpdateUserDTO updateDTO) UserDTO
        +loadUserByUsername(String username) UserDetails
    }

    class FriendshipService {
        -FriendshipRepository friendshipRepository
        -UserRepository userRepository
        -FriendshipMapper friendshipMapper
        +sendFriendRequest(Long requesterId, String addresseeUsername) FriendshipDTO
        +acceptFriendRequest(Long friendshipId, Long userId) FriendshipDTO
    }

    %% Controllers
    class UserController {
        -UserService userService
        +createUser(UserDTO userDTO) ResponseEntity
        +getUserById(Long id) ResponseEntity
        +searchUsers(String query, int page, int size) ResponseEntity
    }

    class FriendshipController {
        -FriendshipService friendshipService
        +sendRequest(String addresseeUsername) ResponseEntity
        +acceptRequest(Long id) ResponseEntity
    }

    class AuthController {
        -AuthenticationManager authenticationManager
        -JwtUtil jwtUtil
        -UserService userService
        +login(LoginRequest loginRequest) ResponseEntity
    }

    %% Relationships
    User "1" *-- "many" Role : has
    Friendship "1" --> "1" User : requester
    Friendship "1" --> "1" User : addressee
    Friendship "1" --> "1" FriendshipStatus : status

    UserService --> UserRepository : uses
    UserService --> RoleRepository : uses
    FriendshipService --> FriendshipRepository : uses
    FriendshipService --> UserRepository : uses

    UserController --> UserService : uses
    FriendshipController --> FriendshipService : uses
    AuthController --> UserService : uses
```

## Description

- **Core Entities**: The service manages `User`, `Role`, and `Friendship`. `User` implements `UserDetails` for integration with Spring Security.
- **Friendship Logic**: `Friendship` tracks relationships between users with statuses managed via the `FriendshipStatus` enum.
- **Layers**:
    - **Controllers**: Handle HTTP requests and security annotations.
    - **Services**: Contain business logic and interact with repositories.
    - **Repositories**: Standard Spring Data JPA interfaces for data persistence.
    - **Mappers & DTOs**: (Omitted for brevity in diagram) Handle conversion between internal entities and external API responses.
- **Security**: The `AuthController` handles login and token generation, leveraging `JwtUtil` from the `common-security` module.

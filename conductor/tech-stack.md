# Technology Stack: Splitz

## Core Platform
- **Java 21**: The primary programming language for all microservices, chosen for its robust performance, security, and modern features.
- **Spring Boot 3.2.0**: The foundational backend framework, simplifying the development of scalable, microservice-based architectures.

## Security & Authentication
- **Spring Security + JWT**: A robust security layer for stateless authentication and role-based access control, using JSON Web Tokens (JWT) for secure user sessions.
- **BCrypt**: Used for secure password hashing.

## Data Persistence & Management
- **PostgreSQL**: The production-grade relational database for reliable, persistent data storage.
- **H2 Database**: An in-memory database for fast development and testing.
- **Spring Data JPA / Hibernate**: The Object-Relational Mapping (ORM) layer, providing a seamless bridge between the object model and the relational database.
- **Flyway**: Manages database schema migrations, ensuring consistent database versioning across all environments.

## Architecture & Communication
- **Microservices**: A distributed architecture with services for users and expenses, designed for scalability and independent deployment.
- **MapStruct**: A powerful Java bean mapper that simplifies the conversion between DTOs and entities, ensuring a clean separation of concerns.
- **Lombok**: Reduces boilerplate code by automatically generating getters, setters, and other common methods.
- **Springdoc-openapi (Swagger)**: Automatically generates comprehensive API documentation for all services.
- **Mermaid.js**: Used for creating and maintaining version-controlled architectural diagrams (Sequence, ERD, Context).

## Build & Lifecycle
- **Maven**: The primary build tool and dependency management system.
- **Checkstyle & Spotless**: Used for automated code quality checks and formatting consistency.
- **Docker**: Used for containerizing services.

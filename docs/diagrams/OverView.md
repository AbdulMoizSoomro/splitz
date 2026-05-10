# Architectural Overview

This document provides a bird's-eye view of the Splitz project architecture, showing the complete system structure with frontend, microservices, and data layers.

## System Architecture

```mermaid
C4Container
    title Microservices Architecture - Splitz Web Application
    
    Person(user, "User", "Application user")
    
    Container_Boundary(frontend, "Frontend Layer") {
        Container(react, "React Application", "TypeScript/React", "User interface and client-side logic")
    }
    
    Container_Boundary(api_layer, "API Gateway") {
        Container(gateway, "API Gateway", "Spring Cloud Gateway", "Request routing, authentication, rate limiting")
    }
    
    Container_Boundary(services, "Microservices Layer") {
        Container(user_service, "User Service", "Spring Boot", "User management, authentication, profiles")
        Container(order_service, "Order Service", "Spring Boot", "Order processing, payments, fulfillment")
        Container(product_service, "Product Service", "Spring Boot", "Product catalog, inventory, pricing")
        Container(notification_service, "Notification Service", "Spring Boot", "Email, SMS, push notifications")
        Container(auth_service, "Auth Service", "Spring Boot", "JWT token management, authorization")
    }
    
    Container_Boundary(data, "Data Layer") {
        ContainerDb(user_db, "User Database", "PostgreSQL", "User accounts, profiles, authentication data")
        ContainerDb(order_db, "Order Database", "PostgreSQL", "Orders, payments, customer history")
        ContainerDb(product_db, "Product Database", "PostgreSQL", "Products, inventory, categories")
        ContainerDb(notification_db, "Notification Database", "PostgreSQL", "Notification logs, templates, preferences")
    }
    
    %% Frontend to API Gateway
    Rel(user, react, "Uses", "HTTPS")
    Rel(react, gateway, "Makes API requests", "HTTPS/REST")
    
    %% API Gateway to Microservices
    Rel(gateway, user_service, "Routes user requests", "HTTP/JSON")
    Rel(gateway, order_service, "Routes order requests", "HTTP/JSON")
    Rel(gateway, product_service, "Routes product requests", "HTTP/JSON")
    Rel(gateway, notification_service, "Routes notification requests", "HTTP/JSON")
    Rel(gateway, auth_service, "Routes auth requests", "HTTP/JSON")
    
    %% Inter-service communication
    Rel(order_service, product_service, "Checks inventory", "HTTP/JSON")
    Rel(order_service, user_service, "Gets user info", "HTTP/JSON")
    Rel(order_service, notification_service, "Triggers notifications", "HTTP/JSON")
    Rel(auth_service, user_service, "Validates users", "HTTP/JSON")
    
    %% Services to Databases
    Rel(user_service, user_db, "Reads/writes user data", "JDBC/SQL")
    Rel(order_service, order_db, "Reads/writes order data", "JDBC/SQL")
    Rel(product_service, product_db, "Reads/writes product data", "JDBC/SQL")
    Rel(notification_service, notification_db, "Reads/writes notification data", "JDBC/SQL")
    Rel(auth_service, user_db, "Validates credentials", "JDBC/SQL")
    
    %% Response paths
    Rel(user_db, user_service, "Returns user data", "SQL Response")
    Rel(order_db, order_service, "Returns order data", "SQL Response")
    Rel(product_db, product_service, "Returns product data", "SQL Response")
    Rel(notification_db, notification_service, "Returns notification data", "SQL Response")
```

## Architecture Components

### Frontend Layer
- **React Application**: Single-page application built with TypeScript and React, handling user interface and client-side business logic.

### API Gateway Layer
- **API Gateway**: Spring Cloud Gateway providing centralized routing, authentication, rate limiting, and cross-cutting concerns.

### Microservices Layer
- **User Service**: Handles user registration, profile management, and authentication data.
- **Order Service**: Processes orders, payments, and fulfillment workflows.
- **Product Service**: Manages product catalog, inventory, and pricing information.
- **Notification Service**: Handles email, SMS, and push notifications.
- **Auth Service**: Manages JWT tokens, authentication, and authorization.

### Data Layer
- **User Database**: PostgreSQL database for user accounts and authentication data.
- **Order Database**: PostgreSQL database for orders, payments, and customer history.
- **Product Database**: PostgreSQL database for products, inventory, and categories.
- **Notification Database**: PostgreSQL database for notification logs and templates.

## Data Flow

1. **User Request**: User interacts with React frontend via HTTPS
2. **API Gateway**: Routes requests to appropriate microservices via HTTP/JSON
3. **Service Processing**: Each microservice handles its business logic
4. **Database Operations**: Services communicate with their respective PostgreSQL databases via JDBC/SQL
5. **Response Flow**: Data flows back through the same path to the user

## Key Architectural Patterns

- **Microservices**: Each service is independent with its own database
- **API Gateway**: Central entry point for all client requests
- **Database Per Service**: Each microservice owns its data for independence
- **REST Communication**: Services communicate via HTTP/JSON APIs
- **Authentication**: JWT-based authentication managed by the Auth Service
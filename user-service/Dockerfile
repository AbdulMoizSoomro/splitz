# =============================================================================
# SPLITZ User Service Dockerfile
# Multi-stage build for minimal, secure production image
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# Uses Maven to compile the application and create the JAR
# Build from project root: docker build -f user-service/Dockerfile -t splitz/user-service .
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy parent POM (required for dependency management)
COPY pom.xml /build/pom.xml

# Create stub expense-service pom to satisfy parent module list
RUN mkdir -p /build/expense-service && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.splitz</groupId><artifactId>splitz</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>expense-service</artifactId></project>' > /build/expense-service/pom.xml

# Copy user-service POM and source
COPY user-service/pom.xml /build/user-service/pom.xml
COPY user-service/src /build/user-service/src

# Build the application (skip tests - they should run in CI)
WORKDIR /build/user-service
RUN mvn package -DskipTests -B \
    && cp target/*.jar /build/app.jar \
    && java -Djarmode=layertools -jar /build/app.jar extract --destination /build/extracted

# -----------------------------------------------------------------------------
# Stage 2: Runtime (layered - for development builds from source)
# Minimal JRE image with layered JAR for efficient updates
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Create non-root user
RUN addgroup --system --gid 1001 splitz \
    && adduser --system --uid 1001 --ingroup splitz splitz

WORKDIR /app

# Copy layered JAR components (most stable to least stable)
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# Security: Set ownership and switch to non-root user
RUN chown -R splitz:splitz /app
USER splitz

# Expose the service port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application using Spring Boot's layered launcher
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# -----------------------------------------------------------------------------
# Stage 3: Runtime JAR (alternative - for CI/CD with pre-built JAR)
# Use: docker build --target runtime-jar --build-arg JAR_FILE=target/*.jar .
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime-jar

ARG JAR_FILE=target/*.jar

# Security: Create non-root user
RUN addgroup --system --gid 1001 splitz \
    && adduser --system --uid 1001 --ingroup splitz splitz

WORKDIR /app

# Copy pre-built JAR
COPY ${JAR_FILE} app.jar

# Security: Set ownership and switch to non-root user
RUN chown -R splitz:splitz /app
USER splitz

# Expose the service port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# =============================================================================
# SPLITZ Expense Service Dockerfile
# Multi-stage build for minimal, secure production image
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# Uses Maven to compile the application and create the JAR
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy parent POM
COPY pom.xml /build/pom.xml

# Copy common-security (required dependency)
COPY common-security /build/common-security

# Create stub user-service pom to satisfy parent module list
RUN mkdir -p /build/user-service && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.splitz</groupId><artifactId>splitz</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>user-service</artifactId></project>' > /build/user-service/pom.xml

# Copy expense-service POM and source
COPY expense-service/pom.xml /build/expense-service/pom.xml
COPY expense-service/src /build/expense-service/src

# Build the application
WORKDIR /build/expense-service
RUN mvn package -DskipTests -B \
    && cp target/*.jar /build/app.jar \
    && java -Djarmode=layertools -jar /build/app.jar extract --destination /build/extracted

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Create non-root user
RUN addgroup --system --gid 1001 splitz \
    && adduser --system --uid 1001 --ingroup splitz splitz

WORKDIR /app

# Copy layered JAR components
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# Security: Set ownership and switch to non-root user
RUN chown -R splitz:splitz /app
USER splitz

# Expose the service port
EXPOSE 8081

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

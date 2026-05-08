# =============================================================================
# SPLITZ Expense Service Dockerfile
# Optimized for Development and Production
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Base / Dependency Cache
# -----------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS deps

WORKDIR /build

# Copy parent POM and config
COPY pom.xml /build/
COPY config /build/config

# Copy common-security (internal dependency)
COPY common-security/pom.xml /build/common-security/
COPY common-security/src /build/common-security/src

# Copy expense-service POM
COPY expense-service/pom.xml /build/expense-service/

# Create stub for user-service to satisfy parent module list
RUN mkdir -p /build/user-service && \
    echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><parent><groupId>com.splitz</groupId><artifactId>splitz</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>user-service</artifactId></project>' > /build/user-service/pom.xml

# Install common-security and cache dependencies
RUN mvn install -pl common-security -am -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Development (For live-reloading with bind mounts)
# -----------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS dev
WORKDIR /app
COPY --from=deps /root/.m2 /root/.m2
COPY --from=deps /build /build
WORKDIR /build/expense-service
CMD ["sh", "-c", "mvn spring-boot:run -Dspring-boot.run.profiles=${SPRING_PROFILES_ACTIVE:-dev} -DskipTests -Dcheckstyle.skip -Dspotless.skip"]

# -----------------------------------------------------------------------------
# Stage 3: Builder (For production JAR)
# -----------------------------------------------------------------------------
FROM deps AS builder
COPY expense-service/src /build/expense-service/src
WORKDIR /build/expense-service
RUN mvn package -DskipTests -Dcheckstyle.skip -Dspotless.skip -B && \
    cp target/*.jar /build/app.jar && \
    java -Djarmode=layertools -jar /build/app.jar extract --destination /build/extracted

# -----------------------------------------------------------------------------
# Stage 4: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup --system --gid 1001 splitz && adduser --system --uid 1001 --ingroup splitz splitz
WORKDIR /app
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./
RUN chown -R splitz:splitz /app
USER splitz
EXPOSE 8081
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

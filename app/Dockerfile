# ============================================================
# Spring Boot Multi-Stage Dockerfile
#
# Stage 1 — Build: Gradle runs on Java 21 (ADR-004: Gradle 8.14
#           does not support Java 25 as runtime)
# Stage 2 — Run:   JRE runs on Java 25 Alpine (minimal footprint)
# ============================================================

# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
# Gradle 8.14 runtime requires JDK 21. Toolchain auto-provisions JDK 25 for compilation via Foojay resolver.

WORKDIR /build

# Copy Gradle wrapper and build files first (layer cache — only
# re-downloads dependencies when build files change)
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Download dependencies (cached unless build.gradle changes)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q || true

# Copy source and build the fat JAR
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/build/libs/openrouter-gateway.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

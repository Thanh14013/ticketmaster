# ============================================================
#  TICKETMASTER - Dockerfile (Service Registry)
#  Multi-stage build: Build với Maven → Run với JRE slim
# ============================================================

# ── Stage 1: Build ───────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies trước (chỉ copy pom files)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY service-registry/pom.xml service-registry/

# Download deps (sẽ được cache nếu pom không đổi)
RUN mvn dependency:go-offline -pl service-registry -am -q

# Copy source và build
COPY common-lib/src common-lib/src
COPY service-registry/src service-registry/src

RUN mvn clean package -pl service-registry -am \
    -DskipTests \
    -Dmaven.test.skip=true \
    -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: chạy với non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy JAR từ build stage
COPY --from=builder /build/service-registry/target/*.jar app.jar

# Ownership
RUN chown -R appuser:appgroup /app

USER appuser

# JVM tuning cho container
ENV JAVA_OPTS="-Xms128m -Xmx256m \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.output.ansi.enabled=ALWAYS"

EXPOSE 8761

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
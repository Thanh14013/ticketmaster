# ============================================================
#  TICKETMASTER - Dockerfile (API Gateway)
# ============================================================

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/

RUN mvn dependency:go-offline -pl api-gateway -am -q

COPY common-lib/src common-lib/src
COPY api-gateway/src api-gateway/src

RUN mvn clean package -pl api-gateway -am \
    -DskipTests \
    -Dmaven.test.skip=true \
    -q

# ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/api-gateway/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-Xms128m -Xmx384m \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
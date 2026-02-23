# ============================================================
#  TICKETMASTER - Dockerfile (Payment Service)
# ============================================================

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY payment-service/pom.xml payment-service/

RUN mvn dependency:go-offline -pl payment-service -am -q

COPY common-lib/src common-lib/src
COPY payment-service/src payment-service/src

RUN mvn clean package -pl payment-service -am \
    -DskipTests \
    -Dmaven.test.skip=true \
    -q

# ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/payment-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-Xms128m -Xmx512m \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
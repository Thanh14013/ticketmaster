# ============================================================
#  TICKETMASTER - Dockerfile (Event Service)
# ============================================================

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY event-service/pom.xml event-service/

RUN mvn dependency:go-offline -pl event-service -am -q

COPY common-lib/src common-lib/src
COPY event-service/src event-service/src

RUN mvn clean package -pl event-service -am \
    -DskipTests \
    -Dmaven.test.skip=true \
    -q

# ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/event-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

ENV JAVA_OPTS="-Xms128m -Xmx512m \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
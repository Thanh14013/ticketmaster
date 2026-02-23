# ============================================================
#  TICKETMASTER - Dockerfile (Booking Service)
#  Core service - cần nhiều RAM hơn vì Redisson + Quartz
# ============================================================

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY booking-service/pom.xml booking-service/

RUN mvn dependency:go-offline -pl booking-service -am -q

COPY common-lib/src common-lib/src
COPY booking-service/src booking-service/src

RUN mvn clean package -pl booking-service -am \
    -DskipTests \
    -Dmaven.test.skip=true \
    -q

# ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/booking-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

# Booking service cần RAM cao hơn (Redisson, Quartz, Kafka)
ENV JAVA_OPTS="-Xms256m -Xmx768m \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:G1HeapRegionSize=16m \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
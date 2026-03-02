# 🎟️ Ticketmaster – Microservices Backend

A production-grade, event-driven microservices system inspired by Ticketmaster, built with **Java 21** and **Spring Boot 3.2**. The system covers the full ticket-booking lifecycle: event discovery → seat reservation → payment processing → notification delivery.

---

## 📑 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Services](#services)
- [Port Mapping](#port-mapping)
- [Kafka Topics](#kafka-topics)
- [Database Design](#database-design)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [Development Guide](#development-guide)
- [Monitoring](#monitoring)
- [Project Structure](#project-structure)

---

## Architecture Overview

```
                          ┌─────────────────────────────────────────────┐
  Browser / Mobile App    │              Client Layer                    │
                          └──────────────────┬──────────────────────────┘
                                             │ HTTP
                          ┌──────────────────▼──────────────────────────┐
                          │        Nginx (Reverse Proxy / LB)           │
                          │   Rate limiting · Gzip · Security Headers    │
                          └──────────────────┬──────────────────────────┘
                                             │
                          ┌──────────────────▼──────────────────────────┐
                          │     Spring Cloud API Gateway (:8080)        │
                          │  JWT Auth Filter · Rate Limiter (Redis)     │
                          │  Circuit Breaker · Service Discovery        │
                          └──────┬──────┬──────┬──────┬──────┬─────────┘
                                 │      │      │      │      │
             ┌───────────────────┘      │      │      │      └──────────────────┐
             │                          │      │      │                         │
   ┌─────────▼──────┐   ┌───────────────▼──┐   │  ┌───▼───────────┐  ┌────────▼──────────┐
   │  user-service  │   │  event-service   │   │  │payment-service│  │notification-service│
   │    (:8081)     │   │     (:8082)      │   │  │   (:8084)     │  │      (:8085)       │
   └────────────────┘   └──────────────────┘   │  └───────────────┘  └───────────────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  booking-service    │
                                    │      (:8083)        │
                                    └─────────────────────┘

  ┌────────────────────────────────────────────────────────────────────────────────┐
  │                            Infrastructure Layer                               │
  │   PostgreSQL (per-service DB) · Redis (Cache + Distributed Lock)              │
  │   Apache Kafka (Event Broker) · Eureka Service Registry                       │
  │   Prometheus + Grafana (Observability) · Kafka UI                             │
  └────────────────────────────────────────────────────────────────────────────────┘
```

### Design Patterns Applied

| Pattern | Usage |
|---|---|
| **Database per Service** | Each microservice owns its isolated PostgreSQL database |
| **Domain-Driven Design (DDD)** | All services use `interfaces / application / domain / infrastructure` layering |
| **CQRS** | Command handlers (write) separated from query handlers (read) |
| **Event-Driven Architecture** | Services communicate asynchronously via Apache Kafka |
| **Saga Pattern** | Booking → Payment saga with compensating transactions (cancel on failure) |
| **Distributed Lock** | Redis Redisson locks prevent double-booking of seats |
| **Circuit Breaker** | Resilience4j at API Gateway protects downstream services |
| **Outbox / DLQ** | Dead Letter Queues for guaranteed message delivery |
| **Cache-Aside** | Redis caching for seat maps and event details (high-read traffic) |

---

## Tech Stack

### Core

| Technology | Version | Role |
|---|---|---|
| Java | 21 (LTS) | Primary language |
| Spring Boot | 3.2.5 | Application framework |
| Spring Cloud | 2023.0.1 | Microservices infrastructure |
| Maven | 3.x | Multi-module build system |

### Spring Cloud Components

| Component | Purpose |
|---|---|
| Spring Cloud Gateway | API Gateway, JWT filter, rate limiting |
| Spring Cloud Netflix Eureka | Service registry & discovery |
| Spring Cloud Circuit Breaker (Resilience4j) | Fault tolerance |
| Spring Cloud LoadBalancer | Client-side load balancing |

### Infrastructure

| Technology | Version | Role |
|---|---|---|
| PostgreSQL | 16 | Primary relational database (one DB per service) |
| Redis | 7 | Caching, rate limiting, distributed locking |
| Apache Kafka | 7.6.0 (Confluent) | Async event streaming |
| Zookeeper | 7.6.0 | Kafka coordination |
| Liquibase | — | Database schema migrations |

### Libraries & Tools

| Library | Role |
|---|---|
| JJWT 0.12.5 | JWT token generation & validation |
| Redisson 3.29 | Redis distributed lock client |
| Stripe Java 25.3 | Online payment processing |
| MapStruct 1.5.5 | Object mapping (DTO ↔ Entity) |
| Lombok 1.18.32 | Boilerplate reduction |
| Quartz | Persistent job scheduling (booking expiry) |
| Thymeleaf | HTML email templates |

### Observability

| Tool | Version | Role |
|---|---|---|
| Prometheus | 2.51 | Metrics collection |
| Grafana | 10.4.2 | Metrics dashboards |
| Spring Boot Actuator | — | Health checks & metrics endpoint |
| Kafka UI | latest | Kafka topic browser |

---

## Services

### 1. `api-gateway` (Port 8080)
Entry point for all client requests. Responsibilities:
- **JWT Authentication Filter** – validates Bearer token, injects `X-User-Id` and `X-User-Email` headers downstream
- **Rate Limiting** – Redis-based token bucket (10 req/s auth, 50 req/s events, 20 req/s bookings)
- **Circuit Breaker** – per-service fallback with Resilience4j
- **Routing** – path-based routing to all downstream services
- **CORS** – configurable allowed origins

### 2. `user-service` (Port 8081)
Identity & Access Management:
- User registration, login, profile management
- JWT access token (24h) + refresh token (7d)
- Password hashing with BCrypt
- Redis cache for user session tokens

### 3. `event-service` (Port 8082)
Event & Venue Management:
- CRUD for Venues (stadium, theatre, arena)
- CRUD for Events (concerts, sports, shows)
- Seat map generation per section/row
- Full-text search by keyword, city, category with pagination
- Redis cache for seat maps (high-traffic read)
- Kafka consumer: updates seat status from `booking-service`

**Event Status State Machine:** `DRAFT → PUBLISHED → SOLD_OUT / CANCELLED`

**Seat Status State Machine:** `AVAILABLE → LOCKED → BOOKED / AVAILABLE (released)`

### 4. `booking-service` (Port 8083)
Ticket Booking Orchestrator:
- Distributed seat locking with Redis Redisson (configurable TTL)
- Atomic multi-seat reservation with idempotency checks
- 2-minute payment window enforced by Quartz persistent scheduler
- Booking status machine: `PENDING_PAYMENT → CONFIRMED / CANCELLED / EXPIRED`
- Publishes `booking.created`, `booking.confirmed`, `booking.cancelled`, `booking.expired` to Kafka
- Consumes `payment.processed` / `payment.failed` from Kafka
- SSE (Server-Sent Events) endpoint for real-time booking status updates to clients

### 5. `payment-service` (Port 8084)
Payment Processing:
- Stripe integration (PaymentIntent, webhooks)
- Consumes `booking.created` → initiates Stripe payment
- Publishes `payment.processed` / `payment.failed` / `payment.refunded`
- Idempotency keys prevent duplicate charges
- Webhook signature verification

### 6. `notification-service` (Port 8085)
Notification Delivery:
- Consumes Kafka events: `booking.created`, `booking.confirmed`, `booking.cancelled`, `payment.failed`
- Sends HTML emails via SMTP (Thymeleaf templates: `booking-confirmed.html`, `payment-failed.html`)
- SSE push notifications to connected clients
- Persists notification history in `notification_db`

### 7. `service-registry` (Port 8761)
Netflix Eureka server for service discovery and registration. All microservices register here and resolve each other by name.

### 8. `common-lib`
Shared library (no separate process). Contains:
- Common DTOs, exceptions, security utilities, response wrappers
- Shared Kafka event schemas

---

## Port Mapping

| Service | Default Port | Description |
|---|---|---|
| Nginx | `80` | Public entry point (production) |
| API Gateway | `8080` | Main developer entry point |
| User Service | `8081` | Auth & user management |
| Event Service | `8082` | Events, venues, seats |
| Booking Service | `8083` | Seat reservation & booking |
| Payment Service | `8084` | Stripe payment processing |
| Notification Service | `8085` | Email & SSE notifications |
| Service Registry (Eureka) | `8761` | Service discovery dashboard |
| PostgreSQL | `5432` | Database (all service DBs) |
| Redis | `6379` | Cache & distributed lock |
| Kafka | `29092` | Kafka broker (external) |
| Kafka UI | `8090` | Topic & message browser |
| Prometheus | `9090` | Metrics scraping |
| Grafana | `3000` | Dashboards |

---

## Kafka Topics

Topics are auto-created on startup by `kafka-init` container via `kafka-setup.sh`.

| Topic | Partitions | Producer | Consumers |
|---|---|---|---|
| `booking.created` | 3 | booking-service | payment-service, notification-service |
| `booking.confirmed` | 3 | booking-service | notification-service, event-service |
| `booking.cancelled` | 3 | booking-service | notification-service, event-service |
| `booking.expired` | 3 | booking-service | notification-service, event-service |
| `payment.processed` | 3 | payment-service | booking-service, notification-service |
| `payment.failed` | 3 | payment-service | booking-service, notification-service |
| `payment.refunded` | 3 | payment-service | notification-service |
| `seat.status.changed` | **6** | booking-service | event-service |
| `notification.email` | 3 | notification-service | notification-service |
| `notification.push` | 3 | notification-service | notification-service |
| `dlq.booking` | 1 | — | (manual inspection) |
| `dlq.payment` | 1 | — | (manual inspection) |
| `dlq.notification` | 1 | — | (manual inspection) |

> `seat.status.changed` uses 6 partitions for parallel consumption due to high traffic volume.  
> DLQ topics retain messages for **30 days** (vs. 7 days for normal topics).

---

## Database Design

Each service owns its isolated PostgreSQL database (Database-per-Service pattern):

| Database | Service | Key Tables |
|---|---|---|
| `user_db` | user-service | `users`, `refresh_tokens` |
| `event_db` | event-service | `venues`, `events`, `sections`, `seats` |
| `booking_db` | booking-service | `bookings`, `booking_items`, `QRTZ_*` |
| `payment_db` | payment-service | `payments`, `payment_transactions` |
| `notification_db` | notification-service | `notifications`, `notification_logs` |

All databases use `UTF-8` encoding, `uuid-ossp` and `pgcrypto` extensions, and are managed via **Liquibase** migrations.

---

## Prerequisites

- **Docker** 24+ and **Docker Compose** v2
- **Java 21** (JDK, for local development without Docker)
- **Maven 3.9+** (for local development)
- **Stripe account** (for payment testing) – [Get test keys](https://dashboard.stripe.com/test/apikeys)
- An **SMTP server** for emails (Gmail, SendGrid, Mailhog for local dev, etc.)

---

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/Thanh14013/ticketmaster.git
cd ticketmaster
```

### 2. Configure environment variables

Copy the example env file and fill in the values:

```bash
cp .env.example .env
```

Edit `.env` with your configuration (see [Environment Variables](#environment-variables) section below).

### 3. Start all services

```bash
docker compose up -d
```

Docker Compose starts services in the correct dependency order:

```
PostgreSQL + Redis + Zookeeper
  → Kafka → kafka-init (creates topics)
    → service-registry
      → api-gateway
        → user-service, event-service, booking-service, payment-service, notification-service
```

### 4. Verify services are running

```bash
docker compose ps
```

All services should show `healthy`. Check the Eureka dashboard:

```
http://localhost:8761
```

### 5. Run a quick health check

```bash
curl http://localhost:8080/actuator/health
```

### Rebuild after code changes

```bash
docker compose up -d --build
```

### Stop and clean up

```bash
# Stop containers (keep volumes)
docker compose down

# Stop and remove all data volumes
docker compose down -v
```

---

## Environment Variables

Create a `.env` file in the project root:

```dotenv
# ── Spring ──────────────────────────────────────────────────────
SPRING_PROFILES_ACTIVE=docker

# ── PostgreSQL ───────────────────────────────────────────────────
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_USER=ticketmaster
POSTGRES_PASSWORD=ticketmaster_secret
POSTGRES_EXTERNAL_PORT=5432

# Service-specific DB names
USER_DB=user_db
EVENT_DB=event_db
BOOKING_DB=booking_db
PAYMENT_DB=payment_db
NOTIFICATION_DB=notification_db

# ── Redis ────────────────────────────────────────────────────────
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=redis_secret
REDIS_EXTERNAL_PORT=6379

# ── Kafka ────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KAFKA_EXTERNAL_PORT=29092
KAFKA_GROUP_EVENT=event-service-group
KAFKA_GROUP_BOOKING=booking-service-group
KAFKA_GROUP_PAYMENT=payment-service-group
KAFKA_GROUP_NOTIFICATION=notification-service-group
KAFKA_UI_PORT=8090

# ── JWT ──────────────────────────────────────────────────────────
JWT_SECRET=YourVeryLongAndSecureJWTSecretKeyThatIsAtLeast256BitsLong!!
JWT_EXPIRATION_MS=86400000          # 24 hours
JWT_REFRESH_EXPIRATION_MS=604800000 # 7 days

# ── Eureka ───────────────────────────────────────────────────────
EUREKA_USERNAME=eureka
EUREKA_PASSWORD=eureka_secret

# ── Service Ports ────────────────────────────────────────────────
SERVICE_REGISTRY_PORT=8761
API_GATEWAY_PORT=8080
USER_SERVICE_PORT=8081
EVENT_SERVICE_PORT=8082
BOOKING_SERVICE_PORT=8083
PAYMENT_SERVICE_PORT=8084
NOTIFICATION_SERVICE_PORT=8085

# ── Booking Business Rules ───────────────────────────────────────
BOOKING_SEAT_LOCK_TTL_MINUTES=2
BOOKING_MAX_SEATS_PER_ORDER=8
BOOKING_PAYMENT_TIMEOUT_MINUTES=2

# ── Stripe ───────────────────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_YOUR_STRIPE_SECRET_KEY
STRIPE_WEBHOOK_SECRET=whsec_YOUR_STRIPE_WEBHOOK_SECRET
STRIPE_CURRENCY=usd

# ── Email / SMTP ─────────────────────────────────────────────────
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM_NAME=Ticketmaster
MAIL_FROM_ADDRESS=noreply@ticketmaster.com

# ── CORS ─────────────────────────────────────────────────────────
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

# ── Monitoring ───────────────────────────────────────────────────
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin_secret
```

---

## Development Guide

### Running services locally (without Docker)

For faster development iteration, run only the infrastructure in Docker and start individual services from your IDE or Maven:

```bash
# Start only infrastructure
docker compose up -d postgres redis kafka zookeeper kafka-init service-registry

# Run a specific service (example: user-service)
cd user-service
mvn spring-boot:run
```

Set the active profile to `local` to use `localhost` for all infrastructure connections.

### Building the project

```bash
# Build all modules
mvn clean package -DskipTests

# Build a specific service
mvn clean package -DskipTests -pl user-service -am
```

### Running tests

```bash
# Run all tests
mvn test

# Run tests for a specific service
mvn test -pl booking-service
```

### Code Architecture (per service)

All services follow **Clean Architecture / DDD** with four layers:

```
interfaces/       ← REST controllers, DTOs (request/response)
application/      ← Use case handlers, commands, Kafka producers/consumers
domain/           ← Pure business logic, aggregates, domain services
infrastructure/   ← DB entities, JPA repositories, cache, external integrations
```

### Useful Docker commands

```bash
# View logs of a specific service
docker logs tm-booking-service -f

# Access PostgreSQL
docker exec -it tm-postgres psql -U ticketmaster -d booking_db

# Access Redis CLI
docker exec -it tm-redis redis-cli -a redis_secret

# Manually re-run Kafka topic setup
docker exec -it tm-kafka bash /kafka-setup.sh
```

---

## Monitoring

| Tool | URL | Credentials |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | `eureka` / `eureka_secret` |
| Kafka UI | http://localhost:8090/kafka-ui | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin_secret` |

Grafana is pre-provisioned with Prometheus as a data source (`infrastructure/monitoring/grafana/provisioning/`). Import JVM and Spring Boot dashboards from Grafana Labs (IDs: `4701`, `12900`) for instant visibility.

All Spring Boot services expose metrics at `/actuator/prometheus` for Prometheus scraping.

---

## Project Structure

```
ticketmaster/
├── pom.xml                          # Parent POM – dependency management for all modules
├── docker-compose.yml               # Full stack deployment
├── docker-compose.override.yml      # Local dev overrides (debug logging, extra ports)
│
├── common-lib/                      # Shared library (DTOs, events, exceptions, security utils)
├── service-registry/                # Netflix Eureka Server
├── api-gateway/                     # Spring Cloud Gateway (JWT, rate-limit, circuit breaker)
│
├── user-service/                    # Authentication & user management → user_db
├── event-service/                   # Events, venues, seat maps → event_db
├── booking-service/                 # Seat reservation & booking → booking_db
├── payment-service/                 # Stripe payment processing → payment_db
├── notification-service/            # Email & SSE notifications → notification_db
│
└── infrastructure/
    ├── postgres/
    │   └── init-databases.sql       # Bootstrap all service databases
    ├── kafka/
    │   └── kafka-setup.sh           # Create all Kafka topics on first start
    ├── nginx/
    │   └── nginx.conf               # Reverse proxy config
    └── monitoring/
        ├── prometheus.yml           # Scrape configs for all services
        └── grafana/
            └── provisioning/        # Auto-provisioned datasources & dashboards
```

---

## API Endpoints (Summary)

| Method | Path | Service | Auth Required |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | user-service | No |
| `POST` | `/api/v1/auth/login` | user-service | No |
| `POST` | `/api/v1/auth/refresh` | user-service | No |
| `GET` | `/api/v1/users/me` | user-service | Yes |
| `GET` | `/api/v1/events` | event-service | No |
| `GET` | `/api/v1/events/{id}` | event-service | No |
| `POST` | `/api/v1/events` | event-service | Yes (Admin) |
| `GET` | `/api/v1/venues/{id}` | event-service | No |
| `GET` | `/api/v1/seats/{eventId}` | event-service | No |
| `POST` | `/api/v1/bookings` | booking-service | Yes |
| `GET` | `/api/v1/bookings/{id}` | booking-service | Yes |
| `DELETE` | `/api/v1/bookings/{id}` | booking-service | Yes |
| `GET` | `/api/v1/bookings/{id}/stream` | booking-service | Yes (SSE) |

Full API documentation is available via Swagger UI at each service:  
`http://localhost:{port}/swagger-ui.html`

---
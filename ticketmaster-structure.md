# Ticketmaster – Cây cấu trúc thư mục đầy đủ

```
ticketmaster/
│
├── pom.xml                                          ← Parent POM (manages all modules)
├── docker-compose.yml
├── docker-compose.override.yml
├── .env
├── .gitignore
├── README.md
│
├── infrastructure/
│   ├── nginx/
│   │   └── nginx.conf
│   ├── kafka/
│   │   └── kafka-setup.sh
│   ├── postgres/
│   │   └── init-databases.sql
│   └── monitoring/
│       ├── prometheus.yml
│       └── grafana/
│           └── provisioning/
│               ├── datasources/
│               │   └── prometheus.yml
│               └── dashboards/
│                   └── dashboards.yml
│
│
├── common-lib/                                      ← Shared JAR (không deploy)
│   ├── pom.xml
│   └── src/main/java/com/ticketmaster/common/
│       ├── dto/
│       │   ├── ApiResponse.java
│       │   ├── PageResponse.java
│       │   └── ErrorResponse.java
│       ├── exception/
│       │   ├── BusinessException.java
│       │   ├── ResourceNotFoundException.java
│       │   └── GlobalExceptionHandler.java
│       ├── event/                                   ← Kafka event contracts (shared)
│       │   ├── BookingCreatedEvent.java
│       │   ├── BookingConfirmedEvent.java
│       │   ├── BookingCancelledEvent.java
│       │   ├── PaymentProcessedEvent.java
│       │   ├── PaymentFailedEvent.java
│       │   └── SeatStatusChangedEvent.java
│       ├── security/
│       │   └── JwtUtils.java
│       └── util/
│           ├── DateUtils.java
│           └── IdGenerator.java
│
│
├── service-registry/                                ← Eureka Server
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/ticketmaster/registry/
│           │   ├── ServiceRegistryApplication.java
│           │   └── config/
│           │       └── SecurityConfig.java          ← Bảo vệ Eureka dashboard
│           └── resources/
│               └── application.yml
│
│
├── api-gateway/                                     ← Spring Cloud Gateway
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/ticketmaster/gateway/
│           │   ├── GatewayApplication.java
│           │   ├── config/
│           │   │   ├── RouteConfig.java
│           │   │   ├── SecurityConfig.java
│           │   │   └── CorsConfig.java
│           │   ├── filter/
│           │   │   ├── AuthenticationFilter.java    ← JWT validation filter
│           │   │   └── LoggingFilter.java
│           │   └── fallback/
│           │       └── FallbackController.java
│           └── resources/
│               └── application.yml
│
│
├── user-service/                                    ← Bounded Context: Identity & Access
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/ticketmaster/user/
│           │   ├── UserServiceApplication.java
│           │   │
│           │   ├── domain/                          ← DDD Domain Layer
│           │   │   ├── model/
│           │   │   │   ├── User.java                ← Aggregate Root
│           │   │   │   ├── UserProfile.java          ← Entity
│           │   │   │   └── UserRole.java             ← Enum Value Object
│           │   │   ├── repository/
│           │   │   │   └── UserRepository.java       ← Domain Repository (interface)
│           │   │   └── service/
│           │   │       └── UserDomainService.java    ← Domain logic (password check, etc.)
│           │   │
│           │   ├── application/                     ← DDD Application Layer
│           │   │   ├── command/
│           │   │   │   ├── RegisterUserCommand.java
│           │   │   │   ├── LoginCommand.java
│           │   │   │   └── UpdateProfileCommand.java
│           │   │   ├── handler/
│           │   │   │   ├── RegisterUserHandler.java
│           │   │   │   ├── LoginHandler.java
│           │   │   │   └── UpdateProfileHandler.java
│           │   │   └── service/
│           │   │       └── UserApplicationService.java
│           │   │
│           │   ├── infrastructure/                  ← DDD Infrastructure Layer
│           │   │   ├── persistence/
│           │   │   │   ├── entity/
│           │   │   │   │   └── UserJpaEntity.java
│           │   │   │   ├── mapper/
│           │   │   │   │   └── UserMapper.java       ← MapStruct
│           │   │   │   └── repository/
│           │   │   │       └── UserJpaRepository.java ← Spring Data JPA
│           │   │   ├── security/
│           │   │   │   ├── JwtService.java
│           │   │   │   ├── CustomUserDetailsService.java
│           │   │   │   └── SecurityConfig.java
│           │   │   └── config/
│           │   │       ├── CacheConfig.java
│           │   │       └── SwaggerConfig.java
│           │   │
│           │   └── interfaces/                      ← DDD Interface Layer
│           │       ├── rest/
│           │       │   ├── AuthController.java
│           │       │   └── UserController.java
│           │       └── dto/
│           │           ├── RegisterRequest.java
│           │           ├── LoginRequest.java
│           │           ├── UpdateProfileRequest.java
│           │           ├── AuthResponse.java
│           │           └── UserResponse.java
│           │
│           └── resources/
│               ├── application.yml
│               └── db/changelog/
│                   ├── db.changelog-master.xml
│                   └── migrations/
│                       └── V001__create_users_table.xml
│
│
├── event-service/                                   ← Bounded Context: Event Management
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/ticketmaster/event/
│           │   ├── EventServiceApplication.java
│           │   │
│           │   ├── domain/
│           │   │   ├── model/
│           │   │   │   ├── Event.java               ← Aggregate Root
│           │   │   │   ├── Venue.java               ← Aggregate Root
│           │   │   │   ├── Seat.java                ← Entity
│           │   │   │   ├── SeatSection.java         ← Entity
│           │   │   │   └── SeatStatus.java          ← Enum Value Object
│           │   │   ├── repository/
│           │   │   │   ├── EventRepository.java
│           │   │   │   ├── VenueRepository.java
│           │   │   │   └── SeatRepository.java
│           │   │   └── service/
│           │   │       └── EventDomainService.java
│           │   │
│           │   ├── application/
│           │   │   ├── command/
│           │   │   │   ├── CreateEventCommand.java
│           │   │   │   ├── UpdateEventCommand.java
│           │   │   │   └── UpdateSeatStatusCommand.java
│           │   │   ├── query/
│           │   │   │   ├── SearchEventsQuery.java
│           │   │   │   └── GetSeatMapQuery.java
│           │   │   ├── handler/
│           │   │   │   ├── CreateEventHandler.java
│           │   │   │   ├── UpdateEventHandler.java
│           │   │   │   ├── SearchEventsHandler.java
│           │   │   │   └── GetSeatMapHandler.java
│           │   │   └── kafka/
│           │   │       └── SeatStatusEventConsumer.java ← Consume từ booking-service
│           │   │
│           │   ├── infrastructure/
│           │   │   ├── persistence/
│           │   │   │   ├── entity/
│           │   │   │   │   ├── EventJpaEntity.java
│           │   │   │   │   ├── VenueJpaEntity.java
│           │   │   │   │   ├── SeatJpaEntity.java
│           │   │   │   │   └── SeatSectionJpaEntity.java
│           │   │   │   ├── mapper/
│           │   │   │   │   ├── EventMapper.java
│           │   │   │   │   ├── VenueMapper.java
│           │   │   │   │   └── SeatMapper.java
│           │   │   │   └── repository/
│           │   │   │       ├── EventJpaRepository.java
│           │   │   │       ├── VenueJpaRepository.java
│           │   │   │       └── SeatJpaRepository.java
│           │   │   ├── cache/
│           │   │   │   ├── EventCacheService.java   ← Cache event info
│           │   │   │   └── SeatCacheService.java    ← Event Seats Cache (Redis)
│           │   │   └── config/
│           │   │       ├── KafkaConfig.java
│           │   │       ├── CacheConfig.java
│           │   │       └── SwaggerConfig.java
│           │   │
│           │   └── interfaces/
│           │       ├── rest/
│           │       │   ├── EventController.java
│           │       │   ├── VenueController.java
│           │       │   └── SeatController.java
│           │       └── dto/
│           │           ├── CreateEventRequest.java
│           │           ├── UpdateEventRequest.java
│           │           ├── EventResponse.java
│           │           ├── VenueResponse.java
│           │           ├── SeatResponse.java
│           │           ├── SeatMapResponse.java
│           │           └── EventSearchRequest.java
│           │
│           └── resources/
│               ├── application.yml
│               └── db/changelog/
│                   ├── db.changelog-master.xml
│                   └── migrations/
│                       ├── V001__create_venues_table.xml
│                       ├── V002__create_events_table.xml
│                       ├── V003__create_seat_sections_table.xml
│                       └── V004__create_seats_table.xml
│
│
├── booking-service/                                 ← Bounded Context: Booking (CORE)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/ticketmaster/booking/
│           │   ├── BookingServiceApplication.java
│           │   │
│           │   ├── domain/
│           │   │   ├── model/
│           │   │   │   ├── Booking.java             ← Aggregate Root
│           │   │   │   ├── BookingItem.java         ← Entity (seat được book)
│           │   │   │   └── BookingStatus.java       ← Enum Value Object
│           │   │   ├── repository/
│           │   │   │   └── BookingRepository.java
│           │   │   └── service/
│           │   │       └── BookingDomainService.java ← Business rules: lock, release
│           │   │
│           │   ├── application/
│           │   │   ├── command/
│           │   │   │   ├── CreateBookingCommand.java
│           │   │   │   ├── ConfirmBookingCommand.java
│           │   │   │   └── CancelBookingCommand.java
│           │   │   ├── handler/
│           │   │   │   ├── CreateBookingHandler.java
│           │   │   │   ├── ConfirmBookingHandler.java
│           │   │   │   └── CancelBookingHandler.java
│           │   │   ├── kafka/
│           │   │   │   ├── BookingEventProducer.java    ← Publish booking.created
│           │   │   │   └── PaymentEventConsumer.java    ← Consume payment.processed
│           │   │   └── scheduler/
│           │   │       └── SeatReleaseScheduler.java    ← Release seat sau 2 phút
│           │   │
│           │   ├── infrastructure/
│           │   │   ├── persistence/
│           │   │   │   ├── entity/
│           │   │   │   │   ├── BookingJpaEntity.java
│           │   │   │   │   └── BookingItemJpaEntity.java
│           │   │   │   ├── mapper/
│           │   │   │   │   └── BookingMapper.java
│           │   │   │   └── repository/
│           │   │   │       └── BookingJpaRepository.java
│           │   │   ├── lock/
│           │   │   │   └── RedissonSeatLockService.java ← Distributed Lock (CRITICAL)
│           │   │   └── config/
│           │   │       ├── KafkaConfig.java
│           │   │       ├── QuartzConfig.java
│           │   │       ├── RedissonConfig.java
│           │   │       └── SwaggerConfig.java
│           │   │
│           │   └── interfaces/
│           │       ├── rest/
│           │       │   └── BookingController.java
│           │       ├── dto/
│           │       │   ├── CreateBookingRequest.java
│           │       │   ├── BookingResponse.java
│           │       │   └── BookingItemResponse.java
│           │       └── sse/
│           │           └── BookingNotificationController.java ← SSE endpoint
│           │
│           └── resources/
│               ├── application.yml
│               └── db/changelog/
│                   ├── db.changelog-master.xml
│                   └── migrations/
│                       ├── V001__create_bookings_table.xml
│                       └── V002__create_booking_items_table.xml
│
│
├── payment-service/                                 ← Bounded Context: Payment
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       └── main/
│           ├── java/com/ticketmaster/payment/
│           │   ├── PaymentServiceApplication.java
│           │   │
│           │   ├── domain/
│           │   │   ├── model/
│           │   │   │   ├── Transaction.java         ← Aggregate Root
│           │   │   │   ├── PaymentMethod.java       ← Value Object
│           │   │   │   └── TransactionStatus.java   ← Enum Value Object
│           │   │   ├── repository/
│           │   │   │   └── TransactionRepository.java
│           │   │   └── service/
│           │   │       └── PaymentDomainService.java
│           │   │
│           │   ├── application/
│           │   │   ├── command/
│           │   │   │   └── ProcessPaymentCommand.java
│           │   │   ├── handler/
│           │   │   │   └── ProcessPaymentHandler.java
│           │   │   ├── kafka/
│           │   │   │   ├── BookingEventConsumer.java    ← Consume booking.created
│           │   │   │   └── PaymentEventProducer.java    ← Publish payment.processed
│           │   │   └── port/
│           │   │       └── PaymentGatewayPort.java      ← Interface (Hexagonal)
│           │   │
│           │   ├── infrastructure/
│           │   │   ├── persistence/
│           │   │   │   ├── entity/
│           │   │   │   │   └── TransactionJpaEntity.java
│           │   │   │   ├── mapper/
│           │   │   │   │   └── TransactionMapper.java
│           │   │   │   └── repository/
│           │   │   │       └── TransactionJpaRepository.java
│           │   │   ├── gateway/
│           │   │   │   └── StripePaymentAdapter.java    ← Implements PaymentGatewayPort
│           │   │   └── config/
│           │   │       ├── KafkaConfig.java
│           │   │       ├── StripeConfig.java
│           │   │       ├── Resilience4jConfig.java
│           │   │       └── SwaggerConfig.java
│           │   │
│           │   └── interfaces/
│           │       ├── rest/
│           │       │   └── PaymentController.java
│           │       └── dto/
│           │           ├── PaymentRequest.java
│           │           └── PaymentResponse.java
│           │
│           └── resources/
│               ├── application.yml
│               └── db/changelog/
│                   ├── db.changelog-master.xml
│                   └── migrations/
│                       └── V001__create_transactions_table.xml
│
│
└── notification-service/                            ← Bounded Context: Notification
    ├── pom.xml
    ├── Dockerfile
    └── src/
        └── main/
            ├── java/com/ticketmaster/notification/
            │   ├── NotificationServiceApplication.java
            │   │
            │   ├── domain/
            │   │   ├── model/
            │   │   │   ├── Notification.java        ← Aggregate Root
            │   │   │   └── NotificationType.java    ← Enum Value Object
            │   │   └── repository/
            │   │       └── NotificationRepository.java
            │   │
            │   ├── application/
            │   │   ├── kafka/
            │   │   │   └── NotificationEventConsumer.java ← Consume mọi events
            │   │   └── service/
            │   │       ├── EmailNotificationService.java
            │   │       └── SseNotificationService.java
            │   │
            │   ├── infrastructure/
            │   │   ├── persistence/
            │   │   │   ├── entity/
            │   │   │   │   └── NotificationJpaEntity.java
            │   │   │   └── repository/
            │   │   │       └── NotificationJpaRepository.java
            │   │   ├── email/
            │   │   │   └── JavaMailSenderAdapter.java
            │   │   ├── sse/
            │   │   │   └── SseEmitterRegistry.java  ← Quản lý SSE connections
            │   │   └── config/
            │   │       ├── KafkaConfig.java
            │   │       ├── MailConfig.java
            │   │       └── SwaggerConfig.java
            │   │
            │   └── interfaces/
            │       └── rest/
            │           └── NotificationController.java ← SSE subscribe endpoint
            │
            └── resources/
                ├── application.yml
                ├── db/changelog/
                │   ├── db.changelog-master.xml
                │   └── migrations/
                │       └── V001__create_notifications_table.xml
                └── templates/email/
                    ├── booking-confirmed.html
                    └── payment-failed.html
```

---

## Tổng kết số lượng file

| Module              | Java files | Config files | Tổng  |
|---------------------|-----------|--------------|-------|
| common-lib          | 13        | 1            | 14    |
| service-registry    | 2         | 1            | 3     |
| api-gateway         | 5         | 1            | 6     |
| user-service        | 18        | 3            | 21    |
| event-service       | 24        | 5            | 29    |
| booking-service     | 20        | 3            | 23    |
| payment-service     | 16        | 2            | 18    |
| notification-service| 14        | 4            | 18    |
| Infrastructure      | —         | 7            | 7     |
| Root                | —         | 4            | 4     |
| **Total**           | **112**   | **31**       | **143** |

---

## Quy tắc DDD áp dụng

```
interfaces/   →   application/   →   domain/
    ↓                  ↓               ↑
  (dto, rest)     (command, handler)  (model, repository)
                       ↓
                 infrastructure/
                 (JPA, Redis, Kafka, Lock)
```

- **Domain layer**: không import Spring, không có @Component → pure Java
- **Application layer**: orchestrate use-cases, gọi domain service + repo
- **Infrastructure layer**: implements domain interfaces (JPA, Redis, Kafka)
- **Interfaces layer**: HTTP controllers, SSE, DTOs mapping

## Thứ tự khởi động Docker

```
postgres + redis + zookeeper
       ↓
    kafka
       ↓
  service-registry (Eureka)
       ↓
  api-gateway
       ↓
  user / event / booking / payment / notification  (song song)
```

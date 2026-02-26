# Booking Service – Workflow Documentation (End-to-End)

> **Mục đích:** Tài liệu này mô tả **toàn bộ luồng hoạt động** của `booking-service` từ A đến Z.
> Sau khi đọc xong, bạn sẽ biết: file nào ở layer nào, gọi cái gì, làm gì, và để đạt mục đích gì.

---

## 📐 Tổng quan kiến trúc (Architecture Overview)

Dự án áp dụng **Clean Architecture / DDD (Domain-Driven Design)** với 4 layer chính:

```
┌──────────────────────────────────────────────────────────────────────┐
│                        INTERFACES LAYER                              │
│  (Điểm vào/ra của service: REST API, SSE, DTOs)                     │
│  BookingController · BookingNotificationController                   │
│  CreateBookingRequest · BookingResponse · BookingItemResponse        │
├──────────────────────────────────────────────────────────────────────┤
│                       APPLICATION LAYER                              │
│  (Orchestration: điều phối các luồng nghiệp vụ)                     │
│  Commands: CreateBookingCommand · ConfirmBookingCommand              │
│            CancelBookingCommand                                      │
│  Handlers: CreateBookingHandler · ConfirmBookingHandler              │
│            CancelBookingHandler                                      │
│  Kafka:    BookingEventProducer · PaymentEventConsumer               │
│  Scheduler: SeatReleaseScheduler (Quartz Job)                        │
├──────────────────────────────────────────────────────────────────────┤
│                         DOMAIN LAYER                                 │
│  (Business rules thuần túy – không phụ thuộc framework nào)         │
│  Models:   Booking (Aggregate Root) · BookingItem · BookingStatus    │
│  Service:  BookingDomainService                                      │
│  Repository: BookingRepository (interface)                           │
├──────────────────────────────────────────────────────────────────────┤
│                      INFRASTRUCTURE LAYER                            │
│  (Kết nối với thế giới bên ngoài: DB, Redis, Kafka config)          │
│  Persistence: BookingJpaEntity · BookingItemJpaEntity                │
│               BookingJpaRepository (implements BookingRepository)    │
│               BookingMapper (MapStruct)                              │
│  Lock:        RedissonSeatLockService                                │
│  Config:      KafkaConfig · QuartzConfig · RedissonConfig            │
│               SwaggerConfig                                          │
└──────────────────────────────────────────────────────────────────────┘
```

### 🔗 Hệ thống bên ngoài tương tác

| Service Ngoài        | Giao tiếp              | Mục đích                                       |
|----------------------|------------------------|------------------------------------------------|
| **API Gateway**      | HTTP (đầu vào)         | Route request, inject `X-User-Id`, `X-User-Email` header |
| **payment-service**  | Kafka (async)          | Nhận `booking.created` → xử lý thanh toán     |
| **event-service**    | Kafka (nhận)           | Nhận `seat.status.changed` → cập nhật trạng thái ghế |
| **notification-service** | Kafka (nhận)       | Nhận `booking.created` → gửi email xác nhận   |
| **PostgreSQL**       | JDBC (HikariCP)        | Lưu trữ dữ liệu booking (booking_db)           |
| **Redis (Redisson)** | TCP                    | Distributed lock cho ghế (seat:lock:{seatId})  |
| **Kafka Broker**     | Producer + Consumer    | Event-driven communication                     |
| **Eureka Server**    | HTTP                   | Service discovery (port 8761)                  |

---

## 🗄️ Database Schema

```
bookings (bảng chính)
├── id              VARCHAR(36)  PRIMARY KEY  – UUID v4
├── user_id         VARCHAR(36)  NOT NULL     – ID user đặt vé
├── user_email      VARCHAR(255) NOT NULL     – Email snapshot (không join user-service)
├── event_id        VARCHAR(36)  NOT NULL     – ID event/concert
├── event_name      VARCHAR(255) NOT NULL     – Tên event snapshot
├── total_amount    DECIMAL(10,2) NOT NULL    – Tổng tiền
├── status          VARCHAR(20)  NOT NULL     – PENDING_PAYMENT|CONFIRMED|CANCELLED|EXPIRED
├── transaction_id  VARCHAR(100) NULLABLE     – Stripe transaction ID (sau khi thanh toán)
├── cancellation_reason VARCHAR(500) NULLABLE – Lý do huỷ
├── expires_at      TIMESTAMPTZ  NOT NULL     – created_at + 2 phút
├── created_at      TIMESTAMPTZ  NOT NULL
└── updated_at      TIMESTAMPTZ  NOT NULL

booking_items (chi tiết từng ghế)
├── id              VARCHAR(36)  PRIMARY KEY
├── booking_id      VARCHAR(36)  NOT NULL  FK → bookings.id
├── seat_id         VARCHAR(36)  NOT NULL  Logical FK → event-service
├── section_id      VARCHAR(36)  NOT NULL
├── seat_row        VARCHAR(5)   NOT NULL  – Snapshot: "A"
├── seat_number     VARCHAR(5)   NOT NULL  – Snapshot: "5"
├── section_name    VARCHAR(100) NOT NULL  – Snapshot: "VIP"
└── price           DECIMAL(10,2) NOT NULL – Giá tại thời điểm booking (immutable)

QRTZ_* (Quartz internal tables – tự động tạo bởi Spring Quartz JDBC)
└── Lưu scheduled jobs để survive service restart
```

---

## 🔄 Booking Status State Machine

```
                        ┌──────────────────────────────────────────┐
                        │                                          │
              CREATE  ──► PENDING_PAYMENT ──► CONFIRMED           │
                              │                  │                 │
                              │                  └─► CANCELLED     │
                              │                    (refund flow)   │
                              ├──────────────────────────────────► │
                              │ user cancel (REST API)             │
                              │                                    │
                              └──────────────────────────────────► │
                                payment failed (Kafka)             │
                                OR Quartz expire (2 phút)          │
                                                                   │
                      EXPIRED ◄──────────────────────────────────┘
                              (chỉ từ PENDING_PAYMENT sau 2 phút)
```

| Transition               | Trigger                                   | Handler                |
|--------------------------|-------------------------------------------|------------------------|
| → PENDING_PAYMENT        | User gọi `POST /api/v1/bookings`          | CreateBookingHandler   |
| PENDING_PAYMENT → CONFIRMED | Kafka `payment.processed`              | ConfirmBookingHandler  |
| PENDING_PAYMENT → CANCELLED | User gọi `DELETE /api/v1/bookings/{id}` | CancelBookingHandler  |
| PENDING_PAYMENT → CANCELLED | Kafka `payment.failed`                 | CancelBookingHandler   |
| PENDING_PAYMENT → EXPIRED | Quartz job sau 2 phút                    | SeatReleaseScheduler   |
| CONFIRMED → CANCELLED    | Refund flow (chưa implement)              | CancelBookingHandler   |

---

## 📋 WORKFLOW 1: Tạo Booking Mới (Happy Path)

**Trigger:** User chọn ghế và nhấn "Đặt vé" → `POST /api/v1/bookings`

```
Client (Browser/App)
        │
        │  POST /api/v1/bookings
        │  Header: Authorization: Bearer <JWT>
        │  Body: { "eventId": "evt-001", "seatIds": ["seat-A1", "seat-A2"] }
        │
        ▼
   [API Gateway]
        │  – Xác thực JWT token
        │  – Inject header: X-User-Id, X-User-Email
        │  – Route đến booking-service:8083
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│  INTERFACES LAYER                                           │
│                                                             │
│  BookingNotificationController (thực ra là BookingController│
│  - file bị đặt nhầm tên trong project)                     │
│  → nhận HTTP request                                        │
│  → đọc @RequestHeader("X-User-Id") và @RequestHeader       │
│     ("X-User-Email") để lấy userId, userEmail              │
│  → parse CreateBookingRequest (eventId + seatIds)           │
│  → tạo CreateBookingCommand                                 │
│  → gọi CreateBookingHandler.handle(command)                 │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER – CreateBookingHandler.handle()          │
│                                                             │
│  BƯỚC 1: VALIDATE                                           │
│  → gọi BookingDomainService.validateSeatCount(seatIds)      │
│     • Kiểm tra: seatIds không rỗng, tối đa 8 ghế           │
│     • Nếu vi phạm → throw BusinessException (400/409)       │
│                                                             │
│  → gọi BookingDomainService.validateUserBookingLimit(       │
│       userId, eventId)                                      │
│     • Query DB: bookingRepository.findByUserIdAndEventId()  │
│     • Nếu user đã có active booking (PENDING/CONFIRMED)     │
│       cho event này → throw BusinessException (CONFLICT)    │
│                                                             │
│  BƯỚC 2: LOCK GHẾ (Distributed Lock)                        │
│  → gọi BookingDomainService.lockSeats(seatIds)              │
│     • Với MỖI seatId trong danh sách:                       │
│       - Gọi RedissonSeatLockService.tryLock(seatId, 2min)   │
│       - Redis: SET "seat:lock:seat-A1" NX EX 120            │
│       - Nếu lock thành công → thêm vào lockedSeats list     │
│       - Nếu lock THẤT BẠI (ghế đang bị người khác giữ):    │
│           * Release tất cả locks đã lấy được                │
│           * throw BusinessException "SEAT_ALREADY_LOCKED"   │
│                                                             │
│  BƯỚC 3: BUILD BOOKING ITEMS                                │
│  → buildBookingItems(command)                               │
│     • Với mỗi seatId → tạo BookingItem (price, row, etc.)   │
│     • [TODO production: gọi event-service để lấy seat info] │
│                                                             │
│  BƯỚC 4: TẠO BOOKING AGGREGATE VÀ LƯU DB                   │
│  → Booking.create(id, userId, email, eventId, items, 2min)  │
│     • Tính totalAmount = sum(items.price)                   │
│     • Set status = PENDING_PAYMENT                          │
│     • Set expiresAt = now + 2 phút                          │
│  → bookingRepository.save(booking)  [trong @Transactional]  │
│     • BookingJpaRepository.save()                           │
│     • BookingMapper.toEntity(): Booking → BookingJpaEntity  │
│     • SpringDataBookingRepository.save() → INSERT vào DB   │
│     • BookingMapper.toDomain(): Entity → Booking            │
│                                                             │
│  [Nếu DB save thất bại → release all locks → throw]         │
│                                                             │
│  BƯỚC 5: PUBLISH seat.status.changed (AVAILABLE → LOCKED)  │
│  → Với mỗi seatId:                                          │
│     BookingEventProducer.publishSeatStatusChanged(          │
│       seatId, eventId, "AVAILABLE", "LOCKED", bookingId)    │
│     • Tạo SeatStatusChangedEvent                            │
│     • kafkaTemplate.send("seat.status.changed", seatId, e)  │
│     • Key = seatId (đảm bảo ordering per seat)              │
│     • → event-service nhận và cập nhật trạng thái ghế      │
│                                                             │
│  BƯỚC 6: PUBLISH booking.created                            │
│  → BookingEventProducer.publishBookingCreated(booking)      │
│     • Tạo BookingCreatedEvent (bookingId, userId, email,    │
│       seatIds, totalAmount, expiresAt, ...)                 │
│     • kafkaTemplate.send("booking.created", bookingId, e)   │
│     • Key = bookingId (ordering per booking)                │
│     • → payment-service nhận, tạo payment intent           │
│     • → notification-service nhận, gửi email xác nhận      │
│                                                             │
│  BƯỚC 7: SCHEDULE QUARTZ JOB                                │
│  → scheduleExpireJob(booking)                               │
│     • Tạo JobDetail với jobData["bookingId"]                │
│     • Tạo Trigger fireAt = booking.expiresAt (sau 2 phút)   │
│     • quartzScheduler.scheduleJob(job, trigger)             │
│     • Job được persist vào QRTZ_* tables trong PostgreSQL   │
│     • Nếu lỗi → chỉ log, không throw (booking đã tạo rồi)  │
│                                                             │
│  RETURN: BookingMapper.toResponse(booking)                  │
│     → BookingResponse DTO (id, status, expiresAt, items...) │
└─────────────────────────────────────────────────────────────┘
        │
        │  HTTP 201 Created
        │  Body: BookingResponse { id, status: "PENDING_PAYMENT",
        │         expiresAt, secondsUntilExpiry: 120, items: [...] }
        ▼
Client nhận response, ngay lập tức gọi:
  GET /api/v1/bookings/{bookingId}/stream  ← subscribe SSE
```

### Rollback Scenarios trong Workflow 1:

| Lỗi tại bước          | Hành động rollback                                          |
|-----------------------|-------------------------------------------------------------|
| Validate thất bại     | Không có gì để rollback, return 400/409 ngay               |
| Lock seat thất bại    | Release tất cả locks đã lấy trước đó, return 409           |
| DB save thất bại      | Release tất cả locks (cleanup), throw 500                  |
| Kafka publish thất bại | Booking đã tạo trong DB, Quartz sẽ expire sau 2 phút       |
| Quartz schedule thất bại | Log error, booking vẫn valid, TTL Redis tự expire       |

---

## 📋 WORKFLOW 2: Theo dõi Booking Real-time (SSE)

**Trigger:** Client gọi ngay sau khi tạo booking thành công

```
Client
  │  GET /api/v1/bookings/{bookingId}/stream
  │  Header: X-User-Id: user-123
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│  INTERFACES LAYER                                           │
│                                                             │
│  BookingNotificationController.subscribeBookingStatus()     │
│  → Validate ownership:                                      │
│     BookingDomainService.getBookingForUser(bookingId,userId)│
│     • findById() từ DB                                      │
│     • Kiểm tra booking.userId == userId                     │
│     • Nếu không khớp → 403 Forbidden                       │
│                                                             │
│  → Tạo SseEmitter (timeout = 5 phút)                        │
│  → Đăng ký callbacks:                                       │
│     • onCompletion → emitters.remove(bookingId)             │
│     • onTimeout → emitters.remove(bookingId)                │
│     • onError → emitters.remove(bookingId)                  │
│                                                             │
│  → emitters.put(bookingId, emitter)                         │
│     [ConcurrentHashMap in-memory store]                     │
│                                                             │
│  → Gửi event ngay lập tức (initial state):                  │
│     sendBookingStatus(emitter, booking, "booking:initial")  │
│     • BookingMapper.toResponse(booking)                     │
│     • emitter.send(event.name("booking:initial").data(...)) │
│                                                             │
│  → scheduleHeartbeat(bookingId, emitter)                    │
│     • Mỗi 30 giây gửi: event.name("heartbeat").data("ping")│
│     • Mục đích: giữ connection qua Nginx/firewall            │
│                                                             │
│  → return emitter (HTTP connection kept open)               │
└─────────────────────────────────────────────────────────────┘
  │
  │  HTTP 200 Content-Type: text/event-stream
  │  Connection vẫn mở...
  │
  │  Nhận được:
  │  event: booking:initial
  │  data: {"id":"...", "status":"PENDING_PAYMENT", ...}
  │
  │  (30 giây sau)
  │  event: heartbeat
  │  data: ping
  │
  │  (Khi payment xử lý xong → xem Workflow 3)
  │  event: booking:confirmed
  │  data: {"id":"...", "status":"CONFIRMED", ...}
  │  [Connection đóng tự động vì trạng thái terminal]
  ▼
```

**SSE Event types:**
| Event name            | Khi nào gửi                        |
|-----------------------|------------------------------------|
| `booking:initial`     | Ngay khi subscribe                 |
| `booking:pending_payment` | (ít dùng)                     |
| `booking:confirmed`   | Sau khi payment thành công         |
| `booking:cancelled`   | Booking bị huỷ                     |
| `booking:expired`     | Quartz expire sau 2 phút           |
| `heartbeat`           | Mỗi 30 giây                       |

---

## 📋 WORKFLOW 3: Xác nhận Booking (Payment Thành công)

**Trigger:** `payment-service` publish Kafka message vào topic `payment.processed`

```
payment-service
  │  Kafka topic: payment.processed
  │  Key: bookingId
  │  Value: PaymentProcessedEvent {
  │    bookingId, transactionId, userId, eventId (kafkaEventId)
  │  }
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER – PaymentEventConsumer                   │
│                                                             │
│  @KafkaListener(topics = "payment.processed")               │
│  onPaymentProcessed(event, partition, offset, acknowledgment│
│                                                             │
│  → Tạo ConfirmBookingCommand {                              │
│       bookingId, transactionId, userId, kafkaEventId }      │
│  → confirmBookingHandler.handle(command)                    │
│  → Nếu thành công: acknowledgment.acknowledge()             │
│     [Manual ACK – chỉ ACK sau khi DB update xong]          │
│  → Nếu lỗi: throw exception → KHÔNG ACK                    │
│     → Kafka retry (tối đa 3 lần với interval 1s)           │
│     → Sau 3 lần vẫn lỗi → gửi vào payment.processed.DLT   │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER – ConfirmBookingHandler.handle()         │
│                                                             │
│  BƯỚC 1: LOAD BOOKING                                       │
│  → bookingRepository.findById(bookingId)                    │
│  → Nếu không tìm thấy → ResourceNotFoundException          │
│                                                             │
│  BƯỚC 2: IDEMPOTENCY CHECK                                  │
│  → if (booking.isConfirmed()) return ngay                   │
│     [Kafka có thể retry → xử lý trùng lặp an toàn]         │
│                                                             │
│  BƯỚC 3: DOMAIN STATE TRANSITION                            │
│  → booking.confirm(transactionId)                           │
│     • Kiểm tra: status phải là PENDING_PAYMENT              │
│     • Nếu không → throw BusinessException (CONFLICT)        │
│     • Tạo Booking mới với:                                  │
│       - status = CONFIRMED                                  │
│       - transactionId = từ command                          │
│       - updatedAt = now                                     │
│                                                             │
│  BƯỚC 4: LƯU DB                                             │
│  → bookingRepository.save(confirmed)  [@Transactional]      │
│     • BookingMapper.toEntity() → BookingJpaEntity           │
│     • SpringDataBookingRepository.save() → UPDATE DB        │
│                                                             │
│  BƯỚC 5: PUBLISH seat.status.changed (LOCKED → BOOKED)     │
│  → Với mỗi seatId trong booking:                            │
│     BookingEventProducer.publishSeatStatusChanged(          │
│       seatId, eventId, "LOCKED", "BOOKED", bookingId)       │
│     → event-service nhận, đánh dấu ghế là BOOKED           │
│                                                             │
│  BƯỚC 6: HUỶ QUARTZ JOB                                     │
│  → cancelExpireJob(bookingId)                               │
│     • quartzScheduler.deleteJob(                            │
│         JobKey("expire-{bookingId}", "booking-expire"))      │
│     • Không cần expire nữa vì đã CONFIRMED                  │
│                                                             │
│  BƯỚC 7: RELEASE REDISSON LOCKS                             │
│  → Với mỗi seatId:                                          │
│     seatLockService.unlock(seatId)                          │
│     • Nếu lock đã TTL expire → log debug, bỏ qua           │
│     • Lock không cần thiết nữa (ghế đã BOOKED)             │
│                                                             │
│  RETURN: BookingMapper.toResponse(saved)                    │
└─────────────────────────────────────────────────────────────┘
        │
        │ [SSE Push – nếu client đang subscribe]
        ▼
BookingNotificationController.pushBookingUpdate(bookingId, booking)
  → emitters.get(bookingId) → tìm SseEmitter của client
  → sendBookingStatus(emitter, booking, "booking:confirmed")
     • BookingMapper.toResponse(booking)
     • emitter.send(event.name("booking:confirmed").data(...))
  → booking.isPendingPayment() = false → terminal state
  → emitter.complete() → đóng SSE connection
  → emitters.remove(bookingId)
        │
        ▼
Client nhận:
  event: booking:confirmed
  data: {"id":"...", "status":"CONFIRMED", "transactionId":"txn-xxx", ...}
[Connection đóng]
```

---

## 📋 WORKFLOW 4: Huỷ Booking bởi User

**Trigger:** User nhấn "Huỷ đặt vé" → `DELETE /api/v1/bookings/{id}`

```
Client
  │  DELETE /api/v1/bookings/{bookingId}
  │  Header: X-User-Id: user-123
  │
  ▼
[API Gateway] → inject X-User-Id header → route đến booking-service:8083
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│  INTERFACES LAYER                                           │
│  → nhận request, lấy userId từ header                       │
│  → tạo CancelBookingCommand {                               │
│       bookingId, userId, reason: "User requested",          │
│       systemInitiated: false }                              │
│  → gọi CancelBookingHandler.handle(command)                 │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER – CancelBookingHandler.handle()          │
│                                                             │
│  BƯỚC 1: LOAD BOOKING + VALIDATE OWNERSHIP                  │
│  → systemInitiated = false → phải check ownership          │
│  → BookingDomainService.getBookingForUser(bookingId, userId)│
│     • bookingRepository.findById(bookingId)                 │
│     • Nếu booking.userId ≠ userId → 403 Forbidden           │
│                                                             │
│  BƯỚC 2: IDEMPOTENCY CHECK                                  │
│  → if CANCELLED or EXPIRED → return ngay (safe retry)       │
│                                                             │
│  BƯỚC 3: XÁC ĐỊNH PREVIOUS SEAT STATUS                     │
│  → if booking.isConfirmed() → previousSeatStatus = "BOOKED" │
│  → else → previousSeatStatus = "LOCKED"                    │
│                                                             │
│  BƯỚC 4: DOMAIN STATE TRANSITION                            │
│  → booking.cancel(reason)                                   │
│     • Kiểm tra: KHÔNG thể cancel nếu đã CONFIRMED           │
│       (phải dùng refund flow)                               │
│     • Tạo Booking mới: status = CANCELLED, reason = ...     │
│                                                             │
│  BƯỚC 5: LƯU DB                                             │
│  → bookingRepository.save(cancelled)  [@Transactional]      │
│                                                             │
│  BƯỚC 6: RELEASE REDISSON LOCKS                             │
│  → Với mỗi seatId: seatLockService.unlock(seatId)           │
│                                                             │
│  BƯỚC 7: PUBLISH seat.status.changed → AVAILABLE           │
│  → Với mỗi seatId:                                          │
│     publishSeatStatusChanged(seatId, eventId,               │
│       previousSeatStatus, "AVAILABLE", bookingId)           │
│     → event-service nhận, ghế trở về AVAILABLE             │
│     → user khác có thể đặt ghế này                         │
│                                                             │
│  BƯỚC 8: HUỶ QUARTZ JOB (nếu còn)                          │
│  → quartzScheduler.deleteJob(...)                           │
│                                                             │
│  RETURN: BookingResponse {status: "CANCELLED", ...}         │
└─────────────────────────────────────────────────────────────┘
        │
        │ [SSE Push]
        ▼
BookingNotificationController.pushBookingUpdate()
  → event: booking:cancelled → client nhận thông báo
  → connection đóng
```

---

## 📋 WORKFLOW 5: Tự động Expire Booking (Quartz Scheduler)

**Trigger:** Sau đúng 2 phút kể từ khi booking được tạo, Quartz fire job

```
Thời gian hiện tại >= booking.expiresAt (sau 2 phút)
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│  INFRASTRUCTURE LAYER – Quartz Scheduler                    │
│                                                             │
│  JDBC JobStore (QRTZ_* tables trong PostgreSQL)             │
│  → Tìm trigger với fireTime <= now                          │
│  → Trigger "trigger-{bookingId}" trong group "booking-expire│
│  → Fire job "expire-{bookingId}"                            │
│  → @DisallowConcurrentExecution: chỉ 1 instance chạy       │
│     (cluster-safe nếu có nhiều replicas)                    │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER – SeatReleaseScheduler.execute()         │
│                                                             │
│  → Lấy bookingId từ JobDataMap                              │
│  → bookingRepository.findById(bookingId)                    │
│  → Nếu không tìm thấy → log warning, return (skip)         │
│  → Nếu booking.status ≠ PENDING_PAYMENT:                    │
│     (đã CONFIRMED hoặc đã CANCELLED bởi user)               │
│     → log info, return (không làm gì)                       │
│  → Nếu vẫn PENDING_PAYMENT → cần expire:                   │
│     Tạo CancelBookingCommand {                              │
│       bookingId, userId: null,                              │
│       reason: "Payment timeout – automatically expired",    │
│       systemInitiated: true }                               │
│  → cancelBookingHandler.handle(command)                     │
│     [chạy toàn bộ Workflow Cancel như trên]                 │
│     [systemInitiated=true → bỏ qua ownership check]        │
│  → Nếu lỗi → throw JobExecutionException → Quartz retry    │
└─────────────────────────────────────────────────────────────┘
        │
        │ [gọi CancelBookingHandler – tương tự Workflow 4]
        ▼
  - DB: status = CANCELLED, reason = "Payment timeout..."
  - Redis: unlock tất cả ghế
  - Kafka: seat.status.changed LOCKED → AVAILABLE
  - SSE: event: booking:expired → client nhận thông báo
        │
        ▼
event-service nhận seat.status.changed → ghế về AVAILABLE
User khác có thể đặt ghế này
```

**Recovery scenario (service restart):**
```
booking-service restart sau khi crash
  │
  ▼
Quartz khởi động, đọc QRTZ_* tables từ PostgreSQL
  → Phát hiện jobs có fireTime đã qua (missed jobs)
  → MisfireInstruction = FIRE_NOW → chạy ngay lập tức
  → Tất cả booking đã quá hạn sẽ được expire
```

---

## 📋 WORKFLOW 6: Payment Thất bại

**Trigger:** `payment-service` publish Kafka message vào topic `payment.failed`

```
payment-service
  │  Kafka topic: payment.failed
  │  Value: PaymentFailedEvent {
  │    bookingId, userId, failureReason: "Insufficient funds"
  │  }
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER – PaymentEventConsumer                   │
│                                                             │
│  @KafkaListener(topics = "payment.failed")                  │
│  onPaymentFailed(event, partition, offset, acknowledgment)  │
│                                                             │
│  → Tạo CancelBookingCommand {                               │
│       bookingId,                                            │
│       userId: null,                                         │
│       reason: "Payment failed: Insufficient funds",         │
│       systemInitiated: true }                               │
│  → cancelBookingHandler.handle(command)                     │
│  → acknowledgment.acknowledge() nếu thành công             │
│  → throw nếu lỗi → retry → DLQ                             │
└─────────────────────────────────────────────────────────────┘
        │
        │ [chạy toàn bộ CancelBookingHandler như Workflow 4]
        ▼
  - DB: status = CANCELLED
  - Redis: unlock ghế
  - Kafka: LOCKED → AVAILABLE
  - SSE: event: booking:cancelled
  - Quartz job bị xoá
```

---

## 🔑 Chi tiết Distributed Lock (Redis / Redisson)

**File:** `RedissonSeatLockService.java` (Infrastructure Layer)

```
Khi user A chọn ghế "seat-A1":
┌─────────────────────────────────────────────────────────┐
│  Redis Command:                                         │
│  SET "seat:lock:seat-A1" <value> NX EX 120             │
│       ──────────────────        ──    ──                │
│       lock key                  NX    TTL = 120s       │
│                           (chỉ set nếu chưa tồn tại)   │
└─────────────────────────────────────────────────────────┘

Nếu user B cũng chọn "seat-A1" cùng lúc:
┌─────────────────────────────────────────────────────────┐
│  Redis: key đã tồn tại → NX fail → tryLock() = false   │
│  → BusinessException: "Seat seat-A1 is no longer       │
│     available. Please select different seats."          │
│  → HTTP 409 Conflict                                    │
└─────────────────────────────────────────────────────────┘

Lock key pattern: "seat:lock:{seatId}"
Ví dụ: "seat:lock:abc123-seat-001"

Lock release conditions:
1. Payment thành công → ConfirmBookingHandler.unlock()
2. Booking cancelled → CancelBookingHandler.unlock()
3. TTL tự expire sau 2 phút (fail-safe khi service crash)
```

---

## 📨 Chi tiết Kafka Events

### Topics PRODUCE (booking-service → khác):

**Topic: `booking.created`**
```json
{
  "eventId": "uuid",
  "bookingId": "booking-123",
  "userId": "user-456",
  "userEmail": "user@example.com",
  "eventShowId": "event-789",
  "eventName": "Concert ABC",
  "seatIds": ["seat-A1", "seat-A2"],
  "seatItems": [
    { "seatId": "seat-A1", "sectionId": "...", "seatRow": "A",
      "seatNumber": "1", "sectionName": "VIP", "price": 100.00 }
  ],
  "totalAmount": 200.00,
  "expiresAt": "2024-01-01T10:02:00Z",
  "occurredAt": "2024-01-01T10:00:00Z"
}
```
→ **Consumers:** `payment-service` (tạo payment intent), `notification-service` (gửi email)

**Topic: `seat.status.changed`**
```json
{
  "eventId": "uuid",
  "seatId": "seat-A1",
  "eventShowId": "event-789",
  "previousStatus": "AVAILABLE",  // hoặc LOCKED, BOOKED
  "newStatus": "LOCKED",          // hoặc BOOKED, AVAILABLE
  "bookingId": "booking-123",
  "occurredAt": "2024-01-01T10:00:00Z"
}
```
→ **Consumer:** `event-service` (cập nhật availableSeats, invalidate cache)

**Possible transitions:**
| Khi nào              | previousStatus → newStatus |
|----------------------|---------------------------|
| Tạo booking          | AVAILABLE → LOCKED         |
| Payment thành công   | LOCKED → BOOKED            |
| Booking cancelled    | LOCKED → AVAILABLE         |
| Booking confirmed rồi cancel (refund) | BOOKED → AVAILABLE |

### Topics CONSUME (khác → booking-service):

**Topic: `payment.processed`**
```json
{
  "bookingId": "booking-123",
  "transactionId": "txn-stripe-xxx",
  "userId": "user-456",
  "eventId": "kafka-event-uuid"
}
```
→ Trigger: `ConfirmBookingHandler`

**Topic: `payment.failed`**
```json
{
  "bookingId": "booking-123",
  "userId": "user-456",
  "failureReason": "Insufficient funds"
}
```
→ Trigger: `CancelBookingHandler`

### Kafka DLQ (Dead Letter Queue):
```
Sau 3 lần retry (interval 1s), message bị gửi vào:
  payment.processed  → payment.processed.DLT
  payment.failed     → payment.failed.DLT
```

---

## 🗺️ File Map – Từng File Làm Gì

### INTERFACES LAYER (`interfaces/`)

| File | Package | Làm gì |
|------|---------|--------|
| `BookingController.java` | `interfaces.rest` | REST API endpoints: POST /bookings, GET /bookings/{id}, DELETE /bookings/{id} |
| `BookingNotificationController.java` | `interfaces.sse` | SSE endpoint: GET /bookings/{id}/stream – real-time push |
| `CreateBookingRequest.java` | `interfaces.dto` | Request DTO: nhận `{eventId, seatIds[]}` từ HTTP body |
| `BookingResponse.java` | `interfaces.dto` | Response DTO: trả về cho client (id, status, items, expiresAt...) |
| `BookingItemResponse.java` | `interfaces.dto` | Response DTO: chi tiết 1 ghế trong booking |

### APPLICATION LAYER (`application/`)

| File | Package | Làm gì |
|------|---------|--------|
| `CreateBookingCommand.java` | `application.command` | Data object mang thông tin tạo booking (userId, eventId, seatIds) |
| `ConfirmBookingCommand.java` | `application.command` | Data object mang thông tin confirm booking (bookingId, transactionId) |
| `CancelBookingCommand.java` | `application.command` | Data object mang thông tin cancel booking (bookingId, reason, systemInitiated) |
| `CreateBookingHandler.java` | `application.handler` | **Luồng phức tạp nhất:** validate → lock → save → publish Kafka → schedule Quartz |
| `ConfirmBookingHandler.java` | `application.handler` | Confirm booking: save DB → publish seat BOOKED → cancel Quartz → unlock Redis |
| `CancelBookingHandler.java` | `application.handler` | Cancel booking: save DB → unlock Redis → publish seat AVAILABLE → cancel Quartz |
| `BookingEventProducer.java` | `application.kafka` | Kafka Producer: publish `booking.created` và `seat.status.changed` |
| `PaymentEventConsumer.java` | `application.kafka` | Kafka Consumer: lắng nghe `payment.processed` và `payment.failed` |
| `SeatReleaseScheduler.java` | `application.scheduler` | Quartz Job: tự động expire booking sau 2 phút nếu chưa thanh toán |

### DOMAIN LAYER (`domain/`)

| File | Package | Làm gì |
|------|---------|--------|
| `Booking.java` | `domain.model` | **Aggregate Root:** chứa toàn bộ state và business rules của booking; methods: `create()`, `confirm()`, `cancel()`, `expire()` |
| `BookingItem.java` | `domain.model` | Entity: đại diện 1 ghế trong booking (seatId, price, row, number...); snapshot tại thời điểm booking |
| `BookingStatus.java` | `domain.model` | Enum: PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED |
| `BookingRepository.java` | `domain.repository` | Interface: định nghĩa các query cần thiết (save, findById, findByUserId...) |
| `BookingDomainService.java` | `domain.service` | Business rules cần phối hợp nhiều thứ: lockSeats, releaseSeats, validateUserBookingLimit, getBookingForUser |

### INFRASTRUCTURE LAYER (`infrastructure/`)

| File | Package | Làm gì |
|------|---------|--------|
| `BookingJpaEntity.java` | `infrastructure.persistence.entity` | JPA Entity ánh xạ bảng `bookings` trong DB |
| `BookingItemJpaEntity.java` | `infrastructure.persistence.entity` | JPA Entity ánh xạ bảng `booking_items` trong DB |
| `BookingMapper.java` | `infrastructure.persistence.mapper` | MapStruct mapper: `Booking ↔ BookingJpaEntity`, `Booking → BookingResponse` |
| `BookingJpaRepository.java` | `infrastructure.persistence.repository` | **Adapter:** implements `BookingRepository` interface; chứa `SpringDataBookingRepository` (Spring Data JPA) |
| `RedissonSeatLockService.java` | `infrastructure.lock` | Distributed lock service: `tryLock()`, `unlock()`, `isLocked()`, `extendLock()` |
| `KafkaConfig.java` | `infrastructure.config` | Cấu hình Kafka Consumer (manual ACK, concurrency=3, DLQ) và Producer (acks=all, idempotent) |
| `QuartzConfig.java` | `infrastructure.config` | Cấu hình Quartz Scheduler với Spring DI support (AutowireCapableJobFactory) |
| `RedissonConfig.java` | `infrastructure.config` | Cấu hình Redisson client (connection pool, timeout, retry) |
| `SwaggerConfig.java` | `infrastructure.config` | OpenAPI 3.0 documentation |
| `BookingServiceApplication.java` | `booking` | Main class: `@SpringBootApplication`, `@EnableDiscoveryClient`, `@EnableAsync` |

---

## 🔄 Luồng dữ liệu qua các Layer (Data Flow)

### Khi tạo booking (POST request):

```
HTTP Request
    │
    ▼ (parse JSON)
CreateBookingRequest           ← interfaces.dto
    │
    ▼ (transform + inject userId/email from header)
CreateBookingCommand           ← application.command
    │
    ▼ (orchestration)
CreateBookingHandler           ← application.handler
    │
    ├──► BookingDomainService.validateSeatCount()    ← domain.service
    ├──► BookingDomainService.validateUserBookingLimit()
    │         │
    │         └──► BookingRepository.findByUserIdAndEventId()  ← domain.repository
    │                   │
    │                   └──► BookingJpaRepository → SpringDataBookingRepository → PostgreSQL
    │
    ├──► BookingDomainService.lockSeats()
    │         │
    │         └──► RedissonSeatLockService.tryLock()  ← infrastructure.lock
    │                   │
    │                   └──► Redis: SET seat:lock:{seatId} NX EX 120
    │
    ├──► Booking.create(...)                          ← domain.model
    │
    ├──► BookingRepository.save(booking)              ← domain.repository
    │         │
    │         └──► BookingJpaRepository.save()        ← infrastructure.persistence
    │                   │
    │                   ├──► BookingMapper.toEntity() ← infrastructure.mapper
    │                   ├──► SpringDataBookingRepository.save() → PostgreSQL INSERT
    │                   └──► BookingMapper.toDomain()
    │
    ├──► BookingEventProducer.publishSeatStatusChanged()  ← application.kafka
    │         └──► KafkaTemplate.send("seat.status.changed")
    │
    ├──► BookingEventProducer.publishBookingCreated()
    │         └──► KafkaTemplate.send("booking.created")
    │
    └──► quartzScheduler.scheduleJob(expireJob, trigger)
                └──► QRTZ_* tables trong PostgreSQL (persist)
    │
    ▼ (transform to response)
BookingMapper.toResponse(booking)   ← infrastructure.mapper
    │
    ▼
BookingResponse                     ← interfaces.dto
    │
    ▼
HTTP 201 Response
```

### Khi nhận Kafka event (async):

```
Kafka Broker: "payment.processed"
    │
    ▼
PaymentEventConsumer.onPaymentProcessed()    ← application.kafka
    │
    ▼ (create command)
ConfirmBookingCommand                        ← application.command
    │
    ▼
ConfirmBookingHandler.handle()               ← application.handler
    │
    ├──► BookingRepository.findById()         ← domain.repository → DB
    ├──► booking.confirm(transactionId)       ← domain.model (state transition)
    ├──► BookingRepository.save()             ← domain.repository → DB UPDATE
    ├──► BookingEventProducer.publishSeatStatusChanged() → Kafka
    ├──► quartzScheduler.deleteJob()          → QRTZ_* DB
    └──► RedissonSeatLockService.unlock()     → Redis DEL
    │
    ▼ [SSE Push]
BookingNotificationController.pushBookingUpdate()    ← interfaces.sse
    └──► SseEmitter.send("booking:confirmed") → Client browser
```

---

## ⚙️ Infrastructure & Configuration

### Redis (Distributed Lock)
```yaml
# application.yml
redisson:
  address: redis://localhost:6379
  password: redis_secret
  connection-pool-size: 10
```
- Lock key: `seat:lock:{seatId}`
- TTL: 2 phút (configurable: `booking.seat-lock-ttl-minutes`)
- Non-blocking: `tryLock(waitTime=0)`
- Force unlock nếu lock held bởi thread khác

### Kafka
```yaml
# Consumer
group-id: booking-service-group
auto-offset-reset: earliest
enable.auto.commit: false  ← Manual ACK
concurrency: 3

# Producer  
acks: all         ← durability
retries: 3
enable.idempotence: true
```

### Quartz Scheduler
```yaml
job-store-type: jdbc   ← persist trong PostgreSQL
isClustered: false
threadPool.threadCount: 5
```
- Jobs survive service restart
- MisfireInstruction: FIRE_NOW (chạy ngay khi recover)

### Database (PostgreSQL)
```yaml
url: jdbc:postgresql://localhost:5432/booking_db
hikari:
  maximum-pool-size: 20
  minimum-idle: 5
```
- Schema managed bởi **Liquibase** (`db.changelog-master.xml`)
- Migration: V001 (bookings table) → V002 (booking_items table)

---

## 🔐 Security & Validation

```
Authentication Flow:
Client → API Gateway (validate JWT) → inject headers → booking-service

Headers injected by API Gateway:
  X-User-Id:    user UUID (từ JWT sub claim)
  X-User-Email: user email (từ JWT)

booking-service KHÔNG validate JWT trực tiếp.
Chỉ đọc X-User-Id header và tin tưởng API Gateway.

Ownership validation:
  booking.getUserId().equals(requestUserId)
  → 403 Forbidden nếu không khớp
```

**Business Rules:**
- Tối đa 8 ghế mỗi booking (`booking.max-seats-per-booking`)
- 1 user chỉ có 1 active booking per event
- Booking expire sau 2 phút (`booking.seat-lock-ttl-minutes`)
- CONFIRMED booking không thể cancel trực tiếp (cần refund flow)

---

## 📊 Monitoring & Observability

| Endpoint | Mô tả |
|----------|-------|
| `GET /actuator/health` | Health check (DB, Redis connectivity) |
| `GET /actuator/metrics` | Application metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |
| `GET /swagger-ui.html` | API documentation |

**Logging pattern:**
```
[CREATE_BOOKING] userId=... eventId=... seats=[...]
[LOCK] Seat ... locked successfully (TTL=2min)
[KAFKA] booking.created published | bookingId=... partition=0 offset=42
[QUARTZ] Scheduled expire job for bookingId=... at ...
[SSE] New subscription bookingId=... userId=...
```

---

## 🚀 Startup Sequence

```
1. BookingServiceApplication.main()
   └── SpringApplication.run()

2. Infrastructure beans khởi tạo:
   ├── HikariCP → kết nối PostgreSQL
   ├── Liquibase → chạy migrations (V001, V002)
   ├── RedissonClient → kết nối Redis
   ├── KafkaTemplate, KafkaListenerContainerFactory
   └── SchedulerFactoryBean → Quartz khởi động
       └── Đọc QRTZ_* tables → recover missed jobs (missed bookings → expire ngay)

3. Eureka Client → đăng ký với service-registry:8761

4. Kafka Consumers bắt đầu listen:
   ├── payment.processed → onPaymentProcessed()
   └── payment.failed → onPaymentFailed()

5. Service sẵn sàng nhận request tại port 8083
```

---

## 💡 Tóm tắt nhanh: Ai gọi Ai

```
REST Request
  └─► BookingController
        └─► CreateBookingHandler / CancelBookingHandler
              ├─► BookingDomainService (validate + lock)
              │     ├─► RedissonSeatLockService (Redis)
              │     └─► BookingRepository (DB query)
              ├─► Booking.create() / confirm() / cancel() (domain logic)
              ├─► BookingRepository.save() (DB write)
              │     └─► BookingJpaRepository
              │           ├─► BookingMapper (convert)
              │           └─► SpringDataBookingRepository (JPA)
              ├─► BookingEventProducer (Kafka publish)
              └─► Scheduler.scheduleJob() (Quartz)

Kafka "payment.processed"
  └─► PaymentEventConsumer
        └─► ConfirmBookingHandler
              ├─► BookingRepository.findById() + save()
              ├─► BookingEventProducer (publish LOCKED→BOOKED)
              ├─► Quartz.deleteJob()
              ├─► RedissonSeatLockService.unlock()
              └─► BookingNotificationController.pushBookingUpdate() (SSE)

Quartz Timer (2 phút)
  └─► SeatReleaseScheduler
        └─► CancelBookingHandler
              ├─► BookingRepository.save() (CANCELLED)
              ├─► RedissonSeatLockService.unlock()
              ├─► BookingEventProducer (LOCKED→AVAILABLE)
              └─► BookingNotificationController.pushBookingUpdate() (SSE)
```


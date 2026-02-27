# Interview Q&A – Ticketmaster Microservices

> Tuyển tập câu hỏi phỏng vấn & câu trả lời chính xác về toàn bộ dự án.
> Được tổ chức theo chủ đề từ tổng quan đến chi tiết kỹ thuật.

---

## Mục lục

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Kiến trúc Microservices](#2-kiến-trúc-microservices)
3. [Domain-Driven Design (DDD) & CQRS](#3-domain-driven-design-ddd--cqrs)
4. [API Gateway](#4-api-gateway)
5. [User Service – Xác thực & Phân quyền](#5-user-service--xác-thực--phân-quyền)
6. [Event Service](#6-event-service)
7. [Booking Service – Luồng đặt vé](#7-booking-service--luồng-đặt-vé)
8. [Distributed Lock (Redisson + Redis)](#8-distributed-lock-redisson--redis)
9. [Quartz Scheduler – Tự động hủy ghế](#9-quartz-scheduler--tự-động-hủy-ghế)
10. [Kafka – Event-Driven Architecture](#10-kafka--event-driven-architecture)
11. [Payment Service (Stripe + Resilience4j)](#11-payment-service-stripe--resilience4j)
12. [Notification Service (Email + SSE)](#12-notification-service-email--sse)
13. [Xử lý lỗi & Edge Cases](#13-xử-lý-lỗi--edge-cases)
14. [Database & Migration](#14-database--migration)
15. [Caching Strategy (Redis)](#15-caching-strategy-redis)
16. [Bảo mật](#16-bảo-mật)
17. [Infrastructure & DevOps](#17-infrastructure--devops)
18. [Workflow Chi tiết – Các kịch bản thực tế](#18-workflow-chi-tiết--các-kịch-bản-thực-tế)
    - 18.1. User đặt vé nhưng không thanh toán
    - 18.2. User cancel booking ngay sau khi tạo
    - 18.3. Payment processing thành công
    - 18.4. Payment failed
    - 18.5. Concurrent booking – 100 users chọn cùng 1 ghế
    - 18.6. Redis down trong khi hệ thống đang chạy
    - 18.7. PostgreSQL connection pool exhausted
    - 18.8. Kafka partition rebalancing
    - 18.9. Network partition giữa services
    - 18.10. Booking expired nhưng user vẫn cố thanh toán
    - 18.11. Event bị cancel khi có bookings đang pending
    - 18.12. SSE connection bị drop
    - 18.13. Multi-region deployment latency

---

## 1. Tổng quan dự án

---

**Q: Hãy giới thiệu tổng quan về dự án này.**

**A:** Đây là một hệ thống đặt vé sự kiện trực tuyến được xây dựng theo kiến trúc microservices, lấy cảm hứng từ Ticketmaster. Hệ thống cho phép người dùng đăng ký tài khoản, đăng nhập, xem danh sách sự kiện và sơ đồ ghế, chọn ghế và thực hiện thanh toán. Toàn bộ luồng từ khi người dùng đặt vé cho đến khi nhận email xác nhận được xử lý hoàn toàn tự động thông qua kiến trúc event-driven với Kafka.

Hệ thống gồm 7 microservice chính: `service-registry` (Eureka), `api-gateway`, `user-service`, `event-service`, `booking-service`, `payment-service`, và `notification-service`. Tất cả được đóng gói bằng Docker, quản lý bằng Docker Compose.

---

**Q: Tại sao lại chọn kiến trúc microservices cho dự án này, thay vì monolith?**

**A:** Microservices phù hợp vì:
- **Scalability độc lập:** `event-service` (đọc nhiều) và `booking-service` (write nhiều, cần lock) có đặc thù khác nhau → có thể scale riêng.
- **Domain isolation:** Mỗi service có database riêng (Database per Service pattern), tránh coupling.
- **Fault isolation:** Payment service down không khiến user-service hay event-service bị ảnh hưởng.
- **Công nghệ phù hợp từng service:** Ví dụ `api-gateway` dùng WebFlux/Reactive để non-blocking, các service còn lại dùng MVC truyền thống.

Tuy nhiên đây cũng là trade-off: phức tạp hơn về vận hành, cần service discovery, distributed tracing, v.v.

---

**Q: Hệ thống này xử lý được bao nhiêu service? Thứ tự khởi động thế nào?**

**A:** 7 service (không tính `common-lib` là shared library). Thứ tự khởi động bắt buộc:

```
PostgreSQL + Redis + ZooKeeper → Kafka → service-registry (Eureka) → api-gateway → các service còn lại (song song)
```

Lý do: Kafka cần ZooKeeper, các service cần Eureka để đăng ký, api-gateway cần Eureka để load balance.

---

## 2. Kiến trúc Microservices

---

**Q: `common-lib` là gì và tại sao cần nó?**

**A:** `common-lib` là một shared JAR module (không deploy độc lập) được đóng gói cùng các service khác. Nó chứa:
- **Kafka event contracts:** `BookingCreatedEvent`, `PaymentProcessedEvent`, `PaymentFailedEvent`, `SeatStatusChangedEvent`… – đảm bảo producer và consumer dùng chung schema.
- **Shared DTOs:** `ApiResponse`, `PageResponse`, `ErrorResponse` – format response chuẩn hóa.
- **Shared Exceptions:** `BusinessException`, `ResourceNotFoundException`, `GlobalExceptionHandler`.
- **Utilities:** `JwtUtils`, `DateUtils`, `IdGenerator`.

Nếu không có `common-lib`, mỗi service phải tự định nghĩa event schema → dễ lệch nhau, serialize/deserialize lỗi.

---

**Q: Các service giao tiếp với nhau như thế nào?**

**A:** Có hai kiểu:
1. **Đồng bộ (Synchronous):** Client → API Gateway → Service qua HTTP REST. API Gateway dùng Eureka `lb://service-name` để load balance.
2. **Bất đồng bộ (Asynchronous):** Giữa các service qua Apache Kafka. Ví dụ `booking-service` publish `booking.created` → `payment-service` consume để xử lý thanh toán, `notification-service` consume để gửi email. Cách này decoupled hoàn toàn, một service down không block service kia.

---

**Q: Service discovery hoạt động thế nào trong dự án này?**

**A:** Dùng **Netflix Eureka**. `service-registry` chạy Eureka Server ở port 8761. Mỗi microservice được annotate `@EnableDiscoveryClient` → khi khởi động, service tự đăng ký với Eureka (heartbeat mỗi 30s). API Gateway cấu hình `uri: lb://booking-service` → Spring Cloud LoadBalancer query Eureka để tìm instance có sẵn → forward request. Eureka dashboard có bảo mật bằng Spring Security (basic auth).

---

## 3. Domain-Driven Design (DDD) & CQRS

---

**Q: DDD được áp dụng như thế nào trong dự án?**

**A:** Mỗi service là một **Bounded Context** độc lập. Cấu trúc nội bộ có 4 layer:

- **Domain Layer:** Pure Java (không import Spring/JPA), chứa Aggregate Root (`Booking`, `Event`, `Transaction`), Entity, Value Object, Repository interface. Business rules ở đây.
- **Application Layer:** Orchestrate use-case qua Command/Handler pattern. Gọi domain service và repository nhưng không chứa business logic.
- **Infrastructure Layer:** Implement domain repository interface bằng JPA, kết nối Redis, cấu hình Kafka.
- **Interfaces Layer:** HTTP Controller, SSE endpoint, DTO mapping.

Ví dụ trong `booking-service`: `Booking` là Aggregate Root, có method `confirm()`, `cancel()` chứa state transition rules. Handler chỉ gọi `booking.confirm(transactionId)` chứ không tự chuyển trạng thái.

---

**Q: CQRS được áp dụng như thế nào?**

**A:** Phân tách rõ Command (ghi) và Query (đọc):
- **Command:** `CreateBookingCommand`, `ConfirmBookingCommand`, `CancelBookingCommand` → xử lý bởi tương ứng Handler.
- **Query:** `SearchEventsQuery`, `GetSeatMapQuery` → xử lý bởi Handler riêng, có thể dùng cache khác với store ghi.

Ví dụ `GetSeatMapQuery` đọc từ Redis cache trước, chỉ xuống DB khi cache miss. Trong khi `CreateEventCommand` luôn ghi thẳng vào DB. Hai luồng này tách biệt hoàn toàn.

---

**Q: Hexagonal Architecture (Ports & Adapters) được thể hiện ở đâu?**

**A:** Rõ nhất ở `payment-service`: domain định nghĩa interface `PaymentGatewayPort`, `StripePaymentAdapter` ở infrastructure layer implement interface đó. Nếu muốn đổi từ Stripe sang VNPay, chỉ cần tạo `VNPayPaymentAdapter` mới mà không đụng đến domain hay application layer. Tương tự, `BookingRepository` là port (interface trong domain), `BookingJpaRepository` là adapter (implement bằng Spring Data JPA).

---

## 4. API Gateway

---

**Q: API Gateway làm những gì trong hệ thống này?**

**A:** Là điểm vào duy nhất (`port 8080`), đảm nhận:
1. **JWT Authentication:** Filter `AuthenticationFilter` validate JWT trên mọi protected route trước khi forward. Inject `X-User-Id` và `X-User-Email` vào header để downstream service nhận.
2. **Rate Limiting:** Redis Token Bucket (`RequestRateLimiter` filter). Ví dụ `/api/v1/auth/**` giới hạn 10 req/s, burstCapacity 20.
3. **Circuit Breaker:** Dùng Resilience4j. Khi service down → tự động fallback về `/fallback/{service-name}` thay vì trả lỗi 500.
4. **Routing:** Forward đến đúng service qua `lb://service-name` (Eureka load balance).
5. **CORS:** Xử lý tập trung tại gateway.

Được xây dựng trên **Spring Cloud Gateway + WebFlux** (reactive/non-blocking) để không block thread.

---

**Q: Làm sao API Gateway validate JWT mà không cần gọi User Service?**

**A:** Gateway và User Service dùng chung `JwtUtils` từ `common-lib`, sử dụng cùng secret key (qua environment variable). Gateway extract JWT từ header `Authorization: Bearer <token>`, verify signature bằng secret key, parse claims (userId, email, roles). Nếu hợp lệ → inject header và forward; nếu không → trả 401 ngay. Không cần network call đến user-service → latency thấp.

---

**Q: Rate limiting hoạt động thế nào? Nếu Redis down thì sao?**

**A:** Dùng **Redis Token Bucket algorithm** của Spring Cloud Gateway. Mỗi IP/user được cấp `replenishRate` token mới mỗi giây, `burstCapacity` là số token tối đa có thể tích lũy. Mỗi request tiêu thụ 1 token. Nếu hết token → trả HTTP 429 Too Many Requests.

Nếu Redis down: Spring Cloud Gateway mặc định sẽ để request đi qua (fail-open) để không block toàn bộ traffic. Đây là trade-off giữa availability và rate limiting strictness.

---

**Q: Circuit Breaker trong API Gateway hoạt động thế nào?**

**A:** Cấu hình theo từng route, dùng Resilience4j. Khi một service trả lỗi vượt ngưỡng (ví dụ 50% trong sliding window 10 request), circuit chuyển sang OPEN → mọi request tiếp theo forward ngay đến `fallbackUri: forward:/fallback/service-name` mà không thử gọi service nữa. Sau `waitDurationInOpenState: 30s` chuyển sang HALF-OPEN, thử vài request → nếu thành công thì đóng lại.

---

## 5. User Service – Xác thực & Phân quyền

---

**Q: Luồng đăng ký và đăng nhập hoạt động thế nào?**

**A:**
- **Đăng ký (`POST /api/v1/auth/register`):** Validate request, kiểm tra email chưa tồn tại, hash password bằng **BCrypt**, lưu vào DB. Trả về JWT token ngay sau khi đăng ký thành công.
- **Đăng nhập (`POST /api/v1/auth/login`):** Load user từ DB qua `CustomUserDetailsService`, verify password bằng BCrypt `matches()`. Nếu đúng → generate JWT (có userId, email, roles trong claims, TTL 24h) → trả về client.

JWT được generate bằng **JJWT library**, ký bằng HMAC-SHA256 với secret key từ environment variable.

---

**Q: Tại sao dùng Redis để cache user?**

**A:** User info (profile, roles) được đọc rất nhiều (mọi request qua Gateway đều cần validate user) nhưng ít thay đổi. Cache với key prefix `users::` + userId, TTL hợp lý. Khi user cập nhật profile → invalidate cache entry tương ứng. Giảm đáng kể load lên PostgreSQL.

---

**Q: `X-User-Id` header được inject khi nào và dùng để làm gì?**

**A:** API Gateway inject sau khi validate JWT thành công, trước khi forward request đến downstream service. Downstream service đọc `@RequestHeader("X-User-Id")` để biết user nào đang gọi mà không cần parse JWT lại. Điều này có nghĩa là downstream service **tin tưởng** header này (chỉ Gateway mới có thể inject) → giảm coupling và latency.

---

## 6. Event Service

---

**Q: Event Service quản lý gì và có những API nào?**

**A:** Quản lý 3 Bounded Context con: Venue, Event, Seat.
- `POST /api/v1/venues` – tạo địa điểm
- `POST /api/v1/events` – tạo sự kiện (Admin)
- `GET /api/v1/events?keyword=&city=&category=&page=` – tìm kiếm có phân trang
- `GET /api/v1/events/{id}` – xem chi tiết
- `GET /api/v1/seats/{eventId}/map` – xem sơ đồ ghế (High Traffic endpoint)
- `PUT /api/v1/events/{id}` – cập nhật (Admin)

---

**Q: Seat Map có cache không? TTL là bao nhiêu? Tại sao TTL ngắn?**

**A:** Có, dùng Redis qua `SeatCacheService`. TTL của seat map **chỉ 5 giây**. Lý do TTL ngắn: trạng thái ghế thay đổi rất thường xuyên (mỗi khi có booking mới, ghế chuyển AVAILABLE → LOCKED → BOOKED). Nếu TTL dài, user nhìn thấy ghế "available" nhưng thực ra đã bị người khác lock → trải nghiệm xấu. 5 giây là trade-off giữa cache hiệu quả và data freshness.

Trong khi đó, event info (tên, địa điểm, description) TTL **10 phút** vì thông tin này ít thay đổi hơn.

---

**Q: Trạng thái ghế được cập nhật như thế nào trong event-service?**

**A:** Event-service không trực tiếp nhận HTTP request để đổi trạng thái ghế. Thay vào đó, nó consume Kafka topic `seat.status.changed` từ booking-service. `SeatStatusEventConsumer` xử lý event này, gọi `UpdateSeatStatusCommand` → `Handler` → cập nhật DB + invalidate Redis cache của seat map. Cách này đảm bảo event-service và booking-service hoàn toàn decoupled.

---

**Q: Event status machine có những trạng thái nào?**

**A:** `DRAFT → PUBLISHED → CANCELLED`. Admin tạo event ở trạng thái DRAFT, khi sẵn sàng bán vé thì publish. Khi sự kiện bị hủy → CANCELLED. User chỉ có thể xem và đặt vé events ở trạng thái PUBLISHED.

---

## 7. Booking Service – Luồng đặt vé

---

**Q: Mô tả toàn bộ luồng đặt vé từ khi user nhấn "Đặt vé" đến khi nhận email xác nhận.**

**A:**
1. Client gửi `POST /api/v1/bookings` với `{eventId, seatIds[]}` + JWT.
2. API Gateway validate JWT, inject `X-User-Id` + `X-User-Email`, forward đến booking-service.
3. `BookingController` → tạo `CreateBookingCommand` → gọi `CreateBookingHandler`.
4. Handler validate: số ghế hợp lệ (≤8), user chưa có active booking cho event này.
5. **Distributed Lock:** Với mỗi seatId → `SET seat:lock:{seatId} NX EX 120` qua Redisson. Nếu bất kỳ ghế nào fail → release tất cả lock đã lấy → trả 409.
6. Tạo `Booking` aggregate (status=`PENDING_PAYMENT`, expiresAt=now+2min) → lưu DB.
7. Publish `seat.status.changed` (AVAILABLE→LOCKED) → event-service cập nhật seat map.
8. Publish `booking.created` → payment-service tạo payment intent + notification-service gửi email pending.
9. Schedule Quartz job fire tại `expiresAt`.
10. Trả về `BookingResponse` (HTTP 201) cho client.
11. Client subscribe SSE `GET /api/v1/bookings/{id}/stream` để theo dõi real-time.
12. payment-service xử lý Stripe → publish `payment.processed`.
13. booking-service consumer nhận → `ConfirmBookingHandler`: update DB (CONFIRMED), release lock, cancel Quartz job, publish LOCKED→BOOKED.
14. SSE push `booking:confirmed` → client nhận kết quả ngay.
15. notification-service consumer nhận `payment.processed` → gửi email xác nhận với Thymeleaf template.

---

**Q: Booking status machine có những trạng thái nào? Chuyển đổi thế nào?**

**A:**

| Từ trạng thái | Sang trạng thái | Trigger |
|---|---|---|
| *(new)* | `PENDING_PAYMENT` | User tạo booking |
| `PENDING_PAYMENT` | `CONFIRMED` | Kafka `payment.processed` |
| `PENDING_PAYMENT` | `CANCELLED` | User gọi DELETE hoặc Kafka `payment.failed` |
| `PENDING_PAYMENT` | `EXPIRED` | Quartz job sau 2 phút |
| `CONFIRMED` | `CANCELLED` | Refund flow (chưa implement) |

`CONFIRMED`, `CANCELLED`, `EXPIRED` là terminal states → không thể chuyển tiếp.

---

**Q: Điều gì xảy ra nếu Kafka publish thất bại sau khi đã lưu booking vào DB?**

**A:** Booking vẫn tồn tại trong DB với trạng thái `PENDING_PAYMENT`. Đây là trường hợp eventual consistency. Quartz Scheduler được schedule firing sau 2 phút → tự động expire booking → release lock → publish seat available. Người dùng sẽ nhận thông báo qua SSE rằng booking đã expired. Đây là cơ chế self-healing: dù Kafka không deliver được event, hệ thống vẫn tự dọn sạch sau timeout.

---

**Q: Tại sao giới hạn tối đa 8 ghế mỗi booking?**

**A:** Business rule để tránh scalping (mua vé số lượng lớn để bán lại). Kiểm tra trong `BookingDomainService.validateSeatCount()` ở domain layer. Nếu `seatIds.size() > 8` → throw `BusinessException` với mã lỗi `MAX_SEATS_EXCEEDED`.

---

**Q: User có thể đặt vé cho cùng một event nhiều lần không?**

**A:** Không. `BookingDomainService.validateUserBookingLimit(userId, eventId)` query DB tìm booking có `userId = ? AND eventId = ? AND status IN (PENDING_PAYMENT, CONFIRMED)`. Nếu tồn tại → throw `BusinessException(DUPLICATE_BOOKING)` → trả HTTP 409 Conflict.

---

## 8. Distributed Lock (Redisson + Redis)

---

**Q: Tại sao cần distributed lock cho ghế? Optimistic locking không đủ sao?**

**A:** Distributed lock cần thiết vì hệ thống là microservices chạy trên nhiều instances. Database-level locking hay Java `synchronized` chỉ hoạt động trong một JVM process. Nếu 2 users đồng thời chọn cùng 1 ghế, cả 2 request có thể hit 2 instance khác nhau của booking-service → cần lock chia sẻ qua Redis.

Optimistic locking (version column) cũng được, nhưng sẽ làm một trong 2 user bị lỗi *sau khi* cả 2 đã tốn thời gian xử lý. Distributed lock với `NX EX` fail-fast ngay từ đầu → UX tốt hơn, không lãng phí tài nguyên.

---

**Q: `SET key NX EX 120` hoạt động thế nào?**

**A:** 
- `NX` (Not eXists): Chỉ SET nếu key chưa tồn tại. **Atomic operation** trong Redis → thread-safe.
- `EX 120`: TTL 120 giây (2 phút). Sau 2 phút key tự xóa kể cả khi service crash → không bị ghost lock.

Nếu key đã tồn tại (ghế đang bị người khác hold): Redis trả `nil` → lock thất bại → trả lỗi 409 cho user ngay lập tức.

---

**Q: Điều gì xảy ra nếu service crash sau khi lock ghế nhưng trước khi save DB?**

**A:** Redis TTL 120 giây tự động expire key. Ghế sẽ được release sau tối đa 2 phút mà không cần bất kỳ cleanup thủ công nào. Đây chính là lý do phải đặt TTL bằng đúng timeout của booking (2 phút). Không có data nào bị corrupt vì DB chưa ghi gì.

---

**Q: Nếu lock thành công 3/4 ghế nhưng ghế thứ 4 bị lock fail, xử lý thế nào?**

**A:** Rollback toàn bộ: iterate qua `lockedSeats` list → `seatLockService.unlock(seatId)` cho từng ghế đã lock thành công. Sau đó throw `BusinessException("SEAT_ALREADY_LOCKED", "Ghế số X đang được người khác giữ")` → HTTP 409. Đảm bảo atomicity ở application level: hoặc lock hết tất cả, hoặc không lock cái nào.

---

**Q: Sau khi booking CONFIRMED, có cần giữ lock Redis không?**

**A:** Không. Lock được release tường minh trong `ConfirmBookingHandler` sau khi DB đã update thành CONFIRMED. Tại thời điểm này, ghế đã có trạng thái BOOKED trong DB của event-service (thông qua Kafka). Lock Redis chỉ cần trong khoảng thời gian pending (2 phút). Việc release sớm giải phóng tài nguyên Redis.

---

## 9. Quartz Scheduler – Tự động hủy ghế

---

**Q: Tại sao dùng Quartz, không dùng `@Scheduled` thuần Spring?**

**A:** `@Scheduled` lưu state in-memory → nếu service restart, tất cả pending jobs bị mất → các booking pending không bao giờ được expire. Quartz với **JDBC JobStore** lưu job vào bảng `QRTZ_*` trong PostgreSQL → survive restart. Khi service khởi động lại, Quartz đọc lại jobs từ DB và chạy misfired jobs ngay lập tức (`MisfireInstruction = FIRE_NOW`).

---

**Q: Điều gì xảy ra khi Quartz job chạy nhưng booking đã CONFIRMED trước đó?**

**A:** `SeatReleaseScheduler.execute()` kiểm tra: load booking từ DB → nếu `status != PENDING_PAYMENT` → log info rồi return ngay, không làm gì. Đây là idempotency guard: job có thể fire muộn hoặc bị retry, nhưng chỉ thực sự expire booking đang trong trạng thái đúng.

---

**Q: `@DisallowConcurrentExecution` trên Quartz job có tác dụng gì?**

**A:** Ngăn Quartz chạy đồng thời cùng một job instance (cùng jobKey) trên nhiều thread hoặc nhiều node trong cluster. Nếu job expire-booking-123 đang chạy trên node A, node B sẽ không fire lại job đó cùng lúc. Tránh double-expire gây race condition khi hệ thống scale horizontal.

---

**Q: Nếu Quartz schedule thất bại sau khi booking đã tạo, điều gì xảy ra?**

**A:** Log error nhưng không throw exception (booking đã được ghi vào DB). As a fallback, Redis lock có TTL 120 giây tự expire → ghế release sau 2 phút. Quartz không schedule được → booking sẽ mãi ở trạng thái PENDING_PAYMENT trong DB. Đây là một khoảng trống cần cleanup job định kỳ ở production (cron query tìm booking PENDING_PAYMENT quá expiresAt). Tuy nhiên TTL Redis đảm bảo ghế vẫn được giải phóng cho người dùng khác.

---

## 10. Kafka – Event-Driven Architecture

---

**Q: Những Kafka topic nào được sử dụng? Ai publish, ai consume?**

**A:**

| Topic | Publisher | Consumer(s) |
|---|---|---|
| `booking.created` | booking-service | payment-service, notification-service |
| `booking.confirmed` | booking-service | notification-service |
| `booking.cancelled` | booking-service | notification-service |
| `payment.processed` | payment-service | booking-service |
| `payment.failed` | payment-service | booking-service, notification-service |
| `seat.status.changed` | booking-service | event-service |

---

**Q: Tại sao dùng Kafka thay vì gọi HTTP trực tiếp giữa các service?**

**A:** 
- **Decoupling:** payment-service không cần biết booking-service đang ở địa chỉ nào.
- **Reliability:** Nếu consumer (booking-service) tạm thời down, message vẫn nằm trong Kafka partition → khi service khởi động lại sẽ consume tiếp. Không mất message.
- **Fan-out:** `booking.created` được consume bởi cả payment-service lẫn notification-service mà publisher không cần quan tâm có bao nhiêu consumer.
- **Backpressure:** Consumer tự control tốc độ xử lý (`max.poll.records`).

---

**Q: Manual ACK (acknowledgment) trong Kafka consumer là gì và tại sao dùng?**

**A:** Mặc định Kafka ACK tự động sau khi `poll()` → nếu processing lỗi sau ACK, message bị mất. Dự án dùng manual ACK: `acknowledgment.acknowledge()` chỉ được gọi **sau khi** xử lý logic thành công và DB đã update. Nếu lỗi xảy ra → không ACK → Kafka retry (tối đa 3 lần, interval 1s). Sau 3 lần vẫn lỗi → gửi vào **Dead Letter Topic** (DLT) để xử lý thủ công sau. Đảm bảo at-least-once delivery.

---

**Q: Idempotency khi xử lý Kafka message thế nào?**

**A:** `ConfirmBookingHandler` kiểm tra: `if (booking.isConfirmed()) return ngay`. Tương tự `CancelBookingHandler`: `if (booking.isCancelled() || booking.isExpired()) return`. Kafka at-least-once delivery nghĩa là cùng 1 message có thể được deliver nhiều lần (do retry, rebalance). Idempotency guard đảm bảo xử lý trùng lặp an toàn, chỉ lần đầu có effect, các lần sau là no-op.

---

**Q: Kafka message key được set thế nào và tại sao quan trọng?**

**A:** 
- `booking.created`: key = `bookingId` → đảm bảo ordering per booking.
- `seat.status.changed`: key = `seatId` → đảm bảo ordering per seat (AVAILABLE→LOCKED→BOOKED không bị đảo thứ tự).
- `payment.processed`: key = `bookingId`.

Kafka guarantee ordering trong cùng một partition. Dùng key phù hợp → related messages vào cùng partition → sequential processing.

---

**Q: Dead Letter Topic (DLT) là gì?**

**A:** Khi Kafka consumer retry message tối đa số lần (3 lần) mà vẫn lỗi, message được forward sang topic `{original-topic}.DLT` (ví dụ `payment.processed.DLT`). Điều này ngăn "poison pill" message block toàn bộ partition. DLT message cần được xử lý thủ công: alert devops team, retry logic riêng, hoặc compensating transaction.

---

**Q: Producer `acks=all` có nghĩa là gì?**

**A:** Producer chỉ nhận ACK thành công khi **tất cả in-sync replicas (ISR)** đã ghi message, không chỉ leader. Đây là strongest durability guarantee trong Kafka. Kết hợp với `enable.idempotence: true` → đảm bảo exactly-once semantics ở phía producer. Trade-off: latency cao hơn so với `acks=1` hay `acks=0`.

---

## 11. Payment Service (Stripe + Resilience4j)

---

**Q: Luồng thanh toán hoạt động thế nào?**

**A:**
1. `payment-service` consume `booking.created` event.
2. Lấy `totalAmount` từ event, tạo Stripe PaymentIntent qua Stripe API.
3. Nếu thành công → lưu `Transaction` vào DB với status `SUCCESS`, publish `payment.processed`.
4. Nếu thất bại (Stripe API error, insufficient funds, v.v.) → lưu Transaction status `FAILED`, publish `payment.failed`.
5. booking-service consumer nhận `payment.processed` hoặc `payment.failed` → update booking status tương ứng.

---

**Q: Resilience4j trong payment service cấu hình thế nào?**

**A:** Hai cơ chế bảo vệ cho Stripe API:
- **Circuit Breaker** (`stripe-gateway`): sliding-window 10 requests, failure-rate-threshold 50% → nếu >50% requests thất bại, circuit OPEN trong 30s. Khi OPEN, không gọi Stripe nữa → fail fast. Sau 30s vào HALF-OPEN, thử 3 requests.
- **Retry** (`stripe-gateway`): tối đa 3 lần, wait 2s giữa các lần, chỉ retry cho `IOException` và `SocketTimeoutException` (network error). Không retry cho business errors như "card declined".

---

**Q: Stripe Webhook được xử lý như thế nào?**

**A:** Stripe có thể gọi lại webhook vào `payment-service` khi payment status thay đổi bất đồng bộ (3D Secure, delayed confirmation). `StripePaymentAdapter` verify webhook signature bằng `STRIPE_WEBHOOK_SECRET` để đảm bảo request thực sự từ Stripe. Sau verify → xử lý event tương tự trên → publish Kafka message.

---

**Q: Điều gì xảy ra nếu Stripe API down hoàn toàn?**

**A:** Sau 3 lần retry thất bại (Resilience4j retry) → Circuit Breaker tích lũy failure rate → sau khi vượt ngưỡng 50%, circuit OPEN → tất cả request tiếp theo fail ngay lập tức. `payment-service` publish `payment.failed` với reason "Payment gateway unavailable" → booking-service cancel booking → ghế được giải phóng → user nhận notification thông báo lỗi. Hệ thống tự phục hồi khi Stripe khôi phục sau 30s (HALF-OPEN check).

---

## 12. Notification Service (Email + SSE)

---

**Q: Notification Service gửi những loại thông báo nào và khi nào?**

**A:**

| Event Kafka | Hành động |
|---|---|
| `booking.created` | Gửi email "Đặt vé thành công, vui lòng thanh toán trong 2 phút" |
| `payment.processed` | Gửi email "Xác nhận đặt vé thành công" (Thymeleaf template `booking-confirmed.html`) |
| `payment.failed` | Gửi email "Thanh toán thất bại, vui lòng thử lại" (template `payment-failed.html`) |

Ngoài ra còn có SSE endpoint `GET /api/v1/notifications/stream` cho real-time notifications chung.

---

**Q: Thymeleaf được dùng để làm gì?**

**A:** Render HTML email templates. `booking-confirmed.html` và `payment-failed.html` là Thymeleaf templates với dynamic data (tên user, tên sự kiện, số ghế, tổng tiền). `EmailNotificationService` dùng `TemplateEngine.process()` để render template với dữ liệu thực → gửi qua JavaMailSender (SMTP, port 587, STARTTLS). Email được render server-side, không cần frontend.

---

**Q: SSE (Server-Sent Events) là gì và khác gì WebSocket?**

**A:** 
- **SSE**: Một chiều (server → client), HTTP 1.1, đơn giản, tự động reconnect. Phù hợp cho real-time notification đơn giản.
- **WebSocket**: Hai chiều, cần upgrade protocol, phức tạp hơn.

Dự án dùng SSE vì notification chỉ cần server push (client không cần gửi message lên). `SseEmitter` Spring MVC giữ HTTP connection mở, push events qua. `SseEmitterRegistry` (ConcurrentHashMap) map userId/bookingId → emitter. Heartbeat mỗi 30s để giữ connection qua proxy/firewall.

---

**Q: Nếu client disconnect giữa chừng (tab đóng), SSE emitter xử lý thế nào?**

**A:** `SseEmitter` đăng ký `onCompletion`, `onTimeout`, `onError` callbacks → tất cả đều gọi `emitters.remove(key)`. Khi client disconnect → TCP connection close → `onError` được trigger → emitter bị remove khỏi registry → không còn leak memory. `SseEmitter` timeout được set 5 phút để tự cleanup nếu callback không được trigger.

---

## 13. Xử lý lỗi & Edge Cases

---

**Q: Điều gì xảy ra khi 2 users cùng chọn ghế A1 cùng một lúc?**

**A:** Cả 2 request đến booking-service (có thể cùng instance hoặc khác instance). Cả 2 đều cố `SET seat:lock:seat-A1 NX EX 120`. Redis atomic NX đảm bảo chỉ 1 request thành công. Request kia nhận `nil` → `tryLock()` return false → rollback các ghế đã lock khác → throw `BusinessException(SEAT_ALREADY_LOCKED)` → HTTP 409 về cho user. User thứ hai thấy thông báo "Ghế này đang được người khác giữ".

---

**Q: Điều gì xảy ra nếu booking-service restart trong khi đang có booking PENDING_PAYMENT?**

**A:** Nhiều cơ chế bảo vệ:
1. **Quartz JDBC JobStore:** Jobs được lưu trong PostgreSQL → khi service restart, Quartz đọc lại và chạy misfired jobs (các jobs đã quá giờ) ngay lập tức.
2. **Redis TTL:** Lock tự hết hạn sau 2 phút → ghế được giải phóng kể cả không có cleanup code.
3. **Kafka at-least-once:** Nếu consumer không ACK trước khi crash, message được redeliver khi restart.

---

**Q: Làm sao tránh double booking khi Kafka message bị retry?**

**A:** Idempotency check trong mỗi Handler:
- `ConfirmBookingHandler`: nếu booking đã `CONFIRMED` → return without-op.
- `CancelBookingHandler`: nếu đã `CANCELLED` hoặc `EXPIRED` → return without-op.

Đây là pattern **idempotent consumer**: xử lý cùng message nhiều lần cho kết quả giống nhau lần đầu. Kafka group ID đảm bảo mỗi message chỉ đến một instance trong consumer group, nhưng retry có thể deliver lại cùng message partition.

---

**Q: Điều gì xảy ra nếu notification-service down khi booking.created được publish?**

**A:** Message nằm trong Kafka partition, không mất. Kafka giữ message theo `log.retention.hours` (mặc định 168h = 7 ngày). Khi notification-service khởi động lại, consumer `notification-service-group` tiếp tục consume từ committed offset → gửi email muộn hơn. User có thể nhận email sau vài phút/giờ nhưng vẫn nhận được. Đây là eventual consistency.

---

**Q: Làm sao xử lý concurrent update của booking nếu cả Quartz timer và user cancel cùng lúc?**

**A:** `CancelBookingHandler` được annotate `@Transactional`. Khi load booking và update: Hibernate dùng database-level row locking (`SELECT ... FOR UPDATE` implicit trong transaction). Nếu 2 transaction cùng load booking → 1 transaction phải chờ. Transaction nào commit trước sẽ update CANCELLED/EXPIRED. Transaction kia khi load lại thấy status đã không phải PENDING_PAYMENT → idempotency check → return ngay. Không có race condition dẫn đến corrupt state.

---

**Q: Global Exception Handler hoạt động thế nào?**

**A:** `GlobalExceptionHandler` (trong `common-lib`, được extend ở mỗi service) là `@ControllerAdvice`:
- `BusinessException` → HTTP 400/409 với error code + message.
- `ResourceNotFoundException` → HTTP 404.
- `MethodArgumentNotValidException` (validation lỗi) → HTTP 400 với field errors.
- Mọi `Exception` khác → HTTP 500 với message generic (không expose stack trace ra client).

Format response chuẩn hóa: `{"success": false, "error": {"code": "SEAT_ALREADY_LOCKED", "message": "..."}}`.

---

**Q: Snapshot pattern trong booking item là gì và tại sao dùng?**

**A:** Khi tạo booking, `BookingItem` lưu snapshot: `seatRow`, `seatNumber`, `sectionName`, `price` tại *thời điểm booking*. Không dùng FK sang event-service. Lý do:
- **Data isolation:** Nếu admin đổi tên section hay giá vé sau, booking cũ vẫn hiển thị đúng giá/vị trí lúc đặt.
- **Service decoupling:** booking-service không cần gọi event-service để render booking history.
- **Regulatory compliance:** Hóa đơn, lịch sử giao dịch phải phản ánh đúng thời điểm phát sinh.

---

## 14. Database & Migration

---

**Q: Tại sao mỗi service có database riêng (Database per Service)?**

**A:** Đây là pattern cốt lõi của microservices:
- **Loose coupling:** booking-service thay đổi schema không ảnh hưởng event-service.
- **Polyglot persistence:** Có thể dùng DB khác nhau cho từng service nếu cần.
- **Independent scaling:** Scale booking DB riêng mà không cần scale event DB.
- **Fault isolation:** Event DB down không block booking service (service dùng data riêng).

Downside: không có ACID cross-service → phải dùng Saga pattern hoặc eventual consistency qua Kafka.

---

**Q: Liquibase được dùng thế nào?**

**A:** Mỗi service có `db.changelog-master.xml` làm entry point, include các file migration `V001__create_xxx_table.xml`, `V002__...`. Khi service khởi động, Liquibase tự apply migrations chưa được chạy (dựa trên bảng `DATABASECHANGELOG`). Migrations là XML DSL (không phải SQL raw). Hỗ trợ rollback. `ddl-auto: validate` trong JPA để Hibernate chỉ kiểm tra schema, không tự generate → schema được quản lý hoàn toàn bởi Liquibase.

---

**Q: HikariCP được cấu hình thế nào?**

**A:** Mỗi service cấu hình connection pool khác nhau theo nhu cầu:
- booking-service: `maximum-pool-size: 20` (write-heavy, cần nhiều connection).
- notification-service: `maximum-pool-size: 10` (write ít).
- Connection timeout: 30s. Min idle: 3-5. Pool reuse connection → giảm overhead tạo connection mới cho mỗi request.

---

## 15. Caching Strategy (Redis)

---

**Q: Redis được dùng ở những chỗ nào trong hệ thống?**

**A:**

| Service | Mục đích | TTL |
|---|---|---|
| api-gateway | Rate limiting (Token Bucket) | Managed by algorithm |
| user-service | Cache user profile | Config-based |
| event-service | Cache event info | 10 phút |
| event-service | Cache seat map | 5 giây |
| booking-service | Distributed lock (seat) | 120 giây (2 phút) |

---

**Q: Cache invalidation trong event-service hoạt động thế nào?**

**A:** Khi `SeatStatusEventConsumer` nhận Kafka event và update trạng thái ghế trong DB, `SeatCacheService` evict cache entry cho seat map của event tương ứng (`@CacheEvict`). Lần request tiếp theo sẽ cache miss → load từ DB → cache lại 5 giây. Với event info (TTL 10 phút): khi admin update event → `EventCacheService.evict(eventId)` → cache refresh. Dùng `@Cacheable` và `@CacheEvict` annotations của Spring Cache.

---

**Q: Tại sao seat map TTL chỉ 5 giây chứ không phải 0 (không cache)?**

**A:** Dưới load cao (nhiều user xem cùng 1 event phổ biến), không cache → mỗi request là 1 DB query → DB quá tải. Với TTL 5 giây: 1000 concurrent users xem seat map → chỉ ~1 DB query mỗi 5 giây thay vì 1000. Staleness tối đa 5 giây là chấp nhận được: user nhìn thấy ghế "available" nhưng khi chọn thì bị lock 409 → retry tự nhiên. Seat lock (Redisson) mới là true guard chống double booking, không phải cache.

---

## 16. Bảo mật

---

**Q: JWT được tạo và validate thế nào?**

**A:** User Service dùng JJWT library:
- **Generate:** `Jwts.builder().subject(userId).claim("email", email).claim("roles", roles).issuedAt(now).expiration(now+24h).signWith(secretKey, HS256).compact()`
- **Validate (Gateway):** `Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token)` → nếu expired hoặc signature invalid → exception → 401.

Secret key share giữa user-service (để ký) và api-gateway (để verify) qua environment variable `JWT_SECRET`. Không có network call.

---

**Q: Nếu JWT bị đánh cắp thì sao? Có cơ chế revoke không?**

**A:** Đây là known limitation của stateless JWT. Dự án hiện không implement token revocation. Giải pháp ở production:
1. **Redis blacklist:** Khi logout, add jti (JWT ID) vào Redis blacklist với TTL = remaining token lifetime. Gateway check blacklist trước khi accept token.
2. **Short TTL + refresh token:** Access token TTL ngắn (15 phút), dùng refresh token để lấy access token mới.

Dự án dùng TTL 24h, phù hợp cho demo/portfolio nhưng production cần ngắn hơn.

---

**Q: Eureka Dashboard và các internal endpoint có được bảo vệ không?**

**A:** Có. `service-registry` có `SecurityConfig` yêu cầu basic auth để vào Eureka dashboard (username/password từ env). Internal service kết nối Eureka cũng dùng credentials này trong URL `http://eureka:eureka_secret@localhost:8761/eureka/`. Điều này ngăn người ngoài inspect topology của hệ thống.

---

**Q: CORS được xử lý ở đâu?**

**A:** Tập trung tại API Gateway, cấu hình `globalcors` trong `application.yml`. Hiện tại set `allowedOrigins: "*"` phù hợp cho development. Production cần restrict về domain cụ thể. Middleware `DedupeResponseHeader` ngăn duplicate CORS headers khi cả Gateway và downstream service đều add CORS headers.

---

## 17. Infrastructure & DevOps

---

**Q: Docker Compose cấu trúc thế nào?**

**A:** `docker-compose.yml` là base config, `docker-compose.override.yml` chứa overrides cho development (port mappings, volume mounts). Các service: `postgres`, `redis`, `zookeeper`, `kafka`, `service-registry`, `api-gateway`, `user-service`, `event-service`, `booking-service`, `payment-service`, `notification-service`, `prometheus`, `grafana`. Environment variables được quản lý qua `.env` file.

---

**Q: Multi-stage Docker build hoạt động thế nào trong dự án này?**

**A:** Mỗi service có Dockerfile 2 stage:
- **Stage 1 (builder):** `maven:3.9.6-eclipse-temurin-21-alpine` → copy pom files và download dependencies (layer cache), sau đó copy source và `mvn clean package -DskipTests`.
- **Stage 2 (runtime):** `eclipse-temurin:21-jre-alpine` (image nhỏ hơn, không có Maven/JDK) → copy chỉ JAR file từ stage 1. Tạo non-root user (`appuser`) để chạy process.

Lợi ích: image production chỉ ~200MB thay vì ~600MB nếu dùng full Maven image. Layer cache đảm bảo `mvn dependency:go-offline` chỉ chạy lại khi pom.xml thay đổi.

---

**Q: Prometheus và Grafana được dùng để làm gì?**

**A:** Spring Boot Actuator expose metrics endpoint (`/actuator/prometheus`). Prometheus scrape metrics từ mỗi service theo interval trong `prometheus.yml`. Grafana connect Prometheus làm datasource, visualize dashboards (JVM memory, HTTP request rate, Kafka lag, DB connection pool, custom business metrics). Giúp monitor hệ thống real-time và alert khi có anomaly.

---

**Q: Nginx được dùng để làm gì trong dự án?**

**A:** Nginx đóng vai trò reverse proxy phía trước API Gateway: SSL termination, static content serving, load balancing nếu có nhiều instance api-gateway, connection limit. Cấu hình trong `infrastructure/nginx/nginx.conf`. Giữ API Gateway không cần xử lý SSL/TLS trực tiếp.

---

**Q: Làm sao debug khi một request fail trong môi trường microservices?**

**A:** Dự án có `LoggingFilter` trong API Gateway log mọi request (method, path, userId, response time, status code). Mỗi service log với correlation info. Cải tiến ở production: distributed tracing với **Spring Cloud Sleuth + Zipkin** (không có trong dự án hiện tại) để trace một request xuyên suốt nhiều service bằng `traceId`. Hiện tại có thể dùng `bookingId` làm correlation key để grep logs.

---

**Q: Điều bạn sẽ cải thiện nếu có thêm thời gian?**

**A:** Một số điểm quan trọng:
1. **Distributed Tracing:** Tích hợp Micrometer Tracing + Zipkin để trace request xuyên service.
2. **Outbox Pattern:** Thay vì publish Kafka trực tiếp trong transaction, dùng Transactional Outbox để đảm bảo atomicity DB + Kafka (tránh case DB commit nhưng Kafka fail).
3. **Saga Pattern:** Implement explicit saga cho distributed transaction (booking → payment → confirmation).
4. **Token Revocation:** Redis blacklist cho JWT để support logout.
5. **E2E Testing:** Contract testing giữa services với Spring Cloud Contract hoặc Pact.
6. **Refund Flow:** Implement cancel CONFIRMED booking + Stripe refund.
7. **Multi-instance SSE:** Hiện tại SSE emitter lưu in-memory → không work khi scale booking-service nhiều instance. Cần Redis Pub/Sub để broadcast SSE event đến đúng instance đang giữ connection.

---

## 18. Workflow Chi tiết – Các kịch bản thực tế

---

### 18.1. Kịch bản: User đặt vé nhưng không thanh toán (Happy expiration flow)

**Q: Mô tả chi tiết từng bước khi user đặt vé nhưng không thanh toán trong 2 phút.**

**A:**

**T=0s:** User chọn 2 ghế (A1, A2), nhấn "Đặt vé"
- Request: `POST /api/v1/bookings {eventId, seatIds: [A1, A2]}`
- Gateway validate JWT → inject X-User-Id
- booking-service: `CreateBookingHandler`
  - Validate: ≤8 ghế ✓, user chưa có booking cho event này ✓
  - Redis: `SET seat:lock:A1 NX EX 120` → success
  - Redis: `SET seat:lock:A2 NX EX 120` → success
  - DB: INSERT booking (status=PENDING_PAYMENT, expiresAt=T+120s)
  - Kafka publish: `seat.status.changed` (A1: AVAILABLE→LOCKED, A2: AVAILABLE→LOCKED)
  - Kafka publish: `booking.created`
  - Quartz: schedule job fire tại T+120s
- Response: HTTP 201 với bookingId

**T+1s:** Client subscribe SSE
- Request: `GET /api/v1/bookings/{id}/stream`
- booking-service: validate ownership → tạo SseEmitter → send initial state
- Client nhận: `event: booking:initial, data: {status: PENDING_PAYMENT}`

**T+2s:** payment-service consumer nhận `booking.created`
- Tạo Stripe PaymentIntent
- Giả sử thành công (chỉ tạo intent, chưa charge)
- Trả về cho client payment URL (client có thể bỏ qua)

**T+3s:** notification-service consumer nhận `booking.created`
- Gửi email: "Bạn có 2 phút để thanh toán"

**T+30s, T+60s, T+90s:** SSE heartbeat
- booking-service gửi: `event: heartbeat, data: ping`

**T+120s:** Quartz Scheduler fire job
- `SeatReleaseScheduler.execute(bookingId)`
  - Load booking từ DB → status vẫn là PENDING_PAYMENT
  - Gọi `CancelBookingHandler` với systemInitiated=true
  - DB: UPDATE booking SET status=EXPIRED, reason="Payment timeout"
  - Redis: DEL seat:lock:A1, seat:lock:A2 (có thể đã tự expire)
  - Kafka publish: `seat.status.changed` (A1: LOCKED→AVAILABLE, A2: LOCKED→AVAILABLE)
  - Kafka publish: `booking.cancelled` (hoặc `booking.expired`)
  - Quartz: DELETE job (không cần nữa)

**T+121s:** event-service consumer nhận `seat.status.changed`
- Update DB: seat A1, A2 → AVAILABLE
- Evict Redis cache seat map
- User khác giờ có thể đặt A1, A2

**T+121s:** notification-service consumer nhận `booking.expired`
- Gửi email: "Booking của bạn đã hết hạn"

**T+121s:** SSE push
- booking-service tìm emitter của client (nếu còn kết nối)
- Send: `event: booking:expired, data: {status: EXPIRED}`
- Đóng connection: `emitter.complete()`

**Kết quả cuối:** Booking EXPIRED, ghế A1/A2 về AVAILABLE, user nhận email thông báo.

---

### 18.2. Kịch bản: User cancel booking ngay sau khi tạo

**Q: User đặt vé xong, nhận ra nhầm, cancel ngay lập tức. Workflow diễn ra thế nào?**

**A:**

**T=0s:** User tạo booking thành công (tương tự kịch bản 18.1)
- Booking tạo, status=PENDING_PAYMENT
- Quartz job scheduled tại T+120s
- SSE connection mở

**T+5s:** User nhấn "Hủy booking"
- Request: `DELETE /api/v1/bookings/{id}`
- Gateway inject X-User-Id
- booking-service: `CancelBookingHandler`
  - Load booking, validate ownership (userId khớp ✓)
  - Check status: PENDING_PAYMENT → có thể cancel
  - Idempotency check: chưa cancel/expired ✓
  - DB: `@Transactional` UPDATE booking SET status=CANCELLED, reason="User requested"
  - Redis: `seatLockService.unlock(A1)`, `unlock(A2)`
  - Kafka publish: `seat.status.changed` (A1,A2: LOCKED→AVAILABLE)
  - Kafka publish: `booking.cancelled`
  - Quartz: `deleteJob(expire-booking-{id})` → job bị xóa khỏi QRTZ_* tables
- Response: HTTP 200 với BookingResponse {status: CANCELLED}

**T+6s:** event-service consumer
- Nhận `seat.status.changed` → update seat A1, A2 về AVAILABLE
- Cache evict

**T+6s:** notification-service consumer
- Nhận `booking.cancelled` → gửi email "Bạn đã hủy booking"

**T+6s:** SSE push
- booking-service: `pushBookingUpdate()`
- Send: `event: booking:cancelled`
- emitter.complete() → connection đóng

**T+120s:** Quartz scheduler check
- Job không còn tồn tại trong DB → không có gì xảy ra

**Kết quả:** Booking CANCELLED, ghế release ngay lập tức, không đợi 2 phút. Quartz job bị xóa sớm → không waste tài nguyên.

---

### 18.3. Kịch bản: Payment processing thành công (Happy path hoàn chỉnh)

**Q: Mô tả chi tiết workflow khi user thanh toán thành công.**

**A:**

**T=0s:** User tạo booking → status=PENDING_PAYMENT (như kịch bản 18.1)

**T+10s:** payment-service consumer nhận `booking.created`
- Tạo Stripe PaymentIntent với amount
- Giả sử charge card thành công ngay
- DB: INSERT transaction (status=SUCCESS, stripe_txn_id=ch_xxx)
- Kafka publish: `payment.processed {bookingId, transactionId}`

**T+11s:** booking-service consumer nhận `payment.processed`
- `PaymentEventConsumer.onPaymentProcessed()`
  - Tạo `ConfirmBookingCommand`
  - `ConfirmBookingHandler.handle()`
    - Load booking từ DB
    - Idempotency check: status vẫn là PENDING_PAYMENT ✓
    - Domain: `booking.confirm(transactionId)`
    - DB: `@Transactional` UPDATE booking SET status=CONFIRMED, transaction_id=ch_xxx
    - Kafka publish: `seat.status.changed` (A1,A2: LOCKED→BOOKED)
    - Kafka publish: `booking.confirmed`
    - Quartz: `deleteJob()` → không cần expire nữa
    - Redis: unlock A1, A2 (không cần lock nữa, ghế đã BOOKED vĩnh viễn)
  - Manual ACK: `acknowledgment.acknowledge()` → commit offset

**T+12s:** event-service consumer
- Nhận `seat.status.changed` → update A1, A2: status=BOOKED, bookingId=xxx
- Cache evict → lần query seat map tiếp theo thấy ghế BOOKED

**T+12s:** notification-service consumer
- Nhận `payment.processed` → gửi email confirmation với Thymeleaf template
- Email chứa: tên event, số ghế, QR code (nếu có), transaction ID

**T+12s:** SSE push
- booking-service tìm emitter của client
- Send: `event: booking:confirmed, data: {status: CONFIRMED, transactionId: ch_xxx}`
- `emitter.complete()` → connection đóng (terminal state)

**T+12s:** Client UI
- Nhận SSE event → hiển thị "Đặt vé thành công!"
- Redirect đến trang booking history

**T+120s:** Quartz scheduler check
- Job không tồn tại (đã delete T+11s) → không có gì xảy ra

**Kết quả cuối:** Booking CONFIRMED vĩnh viễn, ghế BOOKED, user nhận email + thông báo real-time. Total latency từ lúc tạo booking đến confirm: ~11-12 giây (phụ thuộc Stripe API).

---

### 18.4. Kịch bản: Payment failed (Insufficient funds)

**Q: User thanh toán nhưng thẻ không đủ tiền. Workflow xử lý thế nào?**

**A:**

**T=0-10s:** User tạo booking, payment-service xử lý (tương tự trên)

**T+10s:** payment-service gọi Stripe API
- Stripe trả về error: `card_declined`, reason: "insufficient_funds"
- Resilience4j retry: thử lại 3 lần (mỗi lần cách 2s)
- Lần 1, 2, 3 đều fail → không retry nữa (business error, không phải network)
- DB: INSERT transaction (status=FAILED, failure_reason="Insufficient funds")
- Kafka publish: `payment.failed {bookingId, failureReason}`

**T+16s:** booking-service consumer nhận `payment.failed`
- `PaymentEventConsumer.onPaymentFailed()`
  - Tạo `CancelBookingCommand` với systemInitiated=true
  - `CancelBookingHandler.handle()`
    - Load booking, status=PENDING_PAYMENT
    - DB: UPDATE status=CANCELLED, reason="Payment failed: Insufficient funds"
    - Redis: unlock seats
    - Kafka publish: `seat.status.changed` (LOCKED→AVAILABLE)
    - Quartz: deleteJob()
  - ACK message

**T+17s:** event-service consumer
- Seats về AVAILABLE, cache evict

**T+17s:** notification-service consumer
- Nhận `payment.failed`
- Gửi email: "Thanh toán thất bại do thẻ không đủ tiền. Vui lòng thử lại."
- Email chứa link để book lại

**T+17s:** SSE push
- Send: `event: booking:cancelled, data: {reason: "Payment failed"}`
- Connection đóng

**Kết quả:** Booking CANCELLED, ghế release cho người khác. User nhận thông báo rõ ràng để xử lý (đổi thẻ, nạp tiền, v.v.).

---

### 18.5. Kịch bản: Concurrent booking – 100 users chọn cùng 1 ghế

**Q: Mô tả chi tiết cơ chế xử lý khi có 100 concurrent requests cùng chọn ghế A1.**

**A:**

**T=0.000s:** 100 users đồng thời click "Đặt vé", chọn ghế A1
- 100 HTTP requests đến API Gateway cùng lúc
- Gateway forward đến booking-service (giả sử có 3 instances)
- Load balancer phân tán: instance-1 nhận 35 requests, instance-2 nhận 33, instance-3 nhận 32

**T=0.010s:** Mỗi instance xử lý requests
- Tất cả 100 requests đều đến bước lock seat: `seatLockService.tryLock("A1")`
- Redis nhận 100 lệnh gần như đồng thời: `SET seat:lock:A1 ... NX EX 120`

**Redis single-threaded processing (atomic):**
- Request #1 (giả sử từ user_42) đến Redis đầu tiên
  - `SET seat:lock:A1 NX` → key chưa tồn tại → **SUCCESS**, return 1
- Request #2 (user_17) đến sau vài microseconds
  - `SET seat:lock:A1 NX` → key đã tồn tại → **FAIL**, return nil
- Request #3, #4, ..., #100: tương tự, tất cả FAIL

**T=0.011s:** Instance xử lý kết quả lock
- **user_42 (thành công):**
  - `tryLock()` return true
  - Tiếp tục workflow: save DB, publish Kafka, schedule Quartz
  - HTTP 201 Created

- **user_17 đến user_100 (99 users thất bại):**
  - `tryLock()` return false
  - `CreateBookingHandler` throw `BusinessException("SEAT_ALREADY_LOCKED")`
  - `@ControllerAdvice` catch → HTTP 409 Conflict
  - Response: `{"error": {"code": "SEAT_ALREADY_LOCKED", "message": "Ghế A1 đang được giữ bởi người dùng khác"}}`

**T=0.050s:** Client UI nhận response
- **user_42:** "Đặt vé thành công, vui lòng thanh toán trong 2 phút"
- **99 users khác:** "Ghế này đã được chọn, vui lòng chọn ghế khác"

**Đặc điểm quan trọng:**
1. **Không có race condition:** Redis atomic NX đảm bảo chỉ 1 success
2. **Fail-fast:** 99 users nhận lỗi trong <50ms, không waste DB writes
3. **No starvation:** User nhanh nhất thắng (fair scheduling tùy Redis network latency)
4. **User experience:** UI có thể auto-retry với ghế khác hoặc hiện danh sách ghế available

---

### 18.6. Kịch bản: Redis down trong khi hệ thống đang chạy

**Q: Điều gì xảy ra khi Redis cluster fail giữa chừng?**

**A:**

**Impact phân tích theo từng service:**

**1. api-gateway (Rate Limiting):**
- `RequestRateLimiter` filter call Redis → **fail**
- Spring Cloud Gateway default behavior: **fail-open** → request được pass qua mà không rate limit
- **Consequence:** Tạm thời mất rate limiting protection → nguy cơ DDoS
- **Mitigation:** Có thể config `deny-empty-key: true` để fail-closed (reject all nếu Redis down)

**2. booking-service (Distributed Lock):**
- User cố đặt vé → `seatLockService.tryLock()` call Redis → **SocketTimeoutException**
- Redisson throw exception
- `CreateBookingHandler` catch → throw `BusinessException("SYSTEM_ERROR")`
- HTTP 503 Service Unavailable
- **Consequence:** **KHÔNG THỂ ĐẶT VÉ MỚI** khi Redis down
- **Mitigation:** 
  - Redisson có thể config cluster replica failover
  - Redis Sentinel cho high availability

**3. event-service (Cache):**
- Seat map cache miss → code try write to Redis → fail
- Spring Cache abstraction throw exception
- Có 2 cách xử lý:
  - Config `cache-null-values: false` → cache exception ignored → query DB directly → **degraded performance** nhưng vẫn work
  - Không config → service crash → **outage**
- **Current setup:** Service vẫn hoạt động nhưng mọi request đều đến DB → latency cao, DB overload nếu traffic lớn

**4. user-service (User cache):**
- Tương tự event-service: cache miss → fallback to DB
- **Consequence:** DB load tăng đột biến, latency cao

**Recovery scenario:**
- **T=0:** Redis down
- **T+30s:** Ops team được alert (Prometheus metrics: redis_up=0)
- **T+2m:** Ops restart Redis hoặc failover sang replica
- **T+2m:** Redis cluster khôi phục
- **T+2m+10s:** Các service reconnect tự động (Lettuce/Redisson auto-reconnect)
- **T+2m+15s:** Hệ thống hoạt động bình thường

**Trade-offs:**
- **Với Redis:** High performance + risk of single point of failure
- **Without Redis:** Không có distributed lock → không scale horizontal được booking-service → bottleneck
- **Best practice:** Redis Cluster 3 master + 3 replica với auto-failover

---

### 18.7. Kịch bản: PostgreSQL connection pool exhausted

**Q: Khi DB connection pool của booking-service hết (20/20 connections đang dùng), request mới xử lý thế nào?**

**A:**

**Setup:** booking-service có `maximum-pool-size: 20`, `connection-timeout: 30000` (30s)

**Scenario:**
- **T=0:** Load cao, 20 concurrent booking requests đang xử lý, mỗi request giữ 1 DB connection
- **T+1s:** Request thứ 21 đến → `CreateBookingHandler` cần DB connection

**HikariCP behavior:**
1. Request 21 gọi `bookingRepository.save()`
2. HikariCP pool: tất cả 20 connections đang busy
3. Thread của request 21 **block** (wait) để lấy connection
4. Timeout counter bắt đầu: 30s

**Case 1: Connection available trong timeout (Happy path):**
- **T+2s:** Request #5 hoàn thành, return connection về pool
- **T+2.001s:** Request 21 được cấp connection từ pool
- Tiếp tục xử lý bình thường

**Case 2: Không có connection trong 30s (Timeout):**
- **T+31s:** Timeout 30s hết
- HikariCP throw `SQLException: Connection is not available, request timed out after 30000ms`
- Spring `@Transactional` catch exception → rollback transaction (không có gì đã commit)
- `@ControllerAdvice` catch → HTTP 503 Service Unavailable
- Response: `{"error": {"code": "SERVICE_UNAVAILABLE", "message": "Database connection timeout"}}`

**Case 3: Connection leak (Worst case):**
- Nếu có bug: transaction không đóng connection, leak accumulates
- Sau vài phút: pool hết vĩnh viễn
- **TẤT CẢ** requests mới đều timeout → service outage
- **Detection:** Prometheus metrics `hikari_connections_active` = 20 (max) liên tục
- **Mitigation:** HikariCP có `leakDetectionThreshold: 60000` (60s) → log warning nếu connection hold quá lâu

**Prevention strategies (production):**
1. **Auto-scaling:** Khi connection pool usage >80% → scale thêm instance
2. **Connection tuning:** Tăng `maximum-pool-size` nếu DB có capacity
3. **Query optimization:** Giảm thời gian hold connection (lazy loading → N+1 problem)
4. **Circuit breaker:** Nếu DB timeout liên tục → circuit OPEN → fail fast, không queue requests
5. **Connection pooling math:** Max pool size = (Core count × 2) + Effective spindle count (SSD: ~10-20)

---

### 18.8. Kịch bản: Kafka partition rebalancing

**Q: Điều gì xảy ra khi Kafka consumer group rebalance (thêm/bớt consumer instance)?**

**A:**

**Scenario:** booking-service có 2 instances, consume topic `payment.processed` (3 partitions: P0, P1, P2)

**Initial state:**
- Instance-1 consume P0, P1
- Instance-2 consume P2

**T=0:** Deploy instance-3 mới (scale to 3 instances)

**T+5s:** Kafka broker detect new consumer join group `booking-service-group`
- Trigger **partition rebalance**
- Kafka coordinator pause message delivery
- Kafka coordinator reassign partitions:
  - Instance-1: P0
  - Instance-2: P1
  - Instance-3: P2

**During rebalance (T+5s đến T+8s):**
- **Không có message nào được consume** từ topic này (tất cả consumers pause)
- Latency spike: `payment.processed` messages accumulate trong Kafka
- Duration: vài giây (lightweight rebalance) đến ~10s (nếu có partition với lag lớn)

**T+8s:** Rebalance complete
- Mỗi instance resume consume từ committed offset
- **Messages không bị mất:** Kafka giữ message, chỉ delay delivery

**Edge case: Message đang xử lý khi rebalance:**
- Instance-1 đang process message từ P1 (chưa ACK)
- Rebalance xảy ra → P1 bị reassign sang Instance-2
- Instance-1 `@KafkaListener` method bị interrupt (Spring Kafka stop polling)
- **Message chưa ACK → committed offset chưa advance**
- Instance-2 nhận P1 → consume lại message đó → **duplicate processing**
- **Idempotency check** trong `ConfirmBookingHandler` cứu: `if (booking.isConfirmed()) return` → no-op

**Impact analysis:**
- **User-facing impact:** ~5-10s delay trong việc booking chuyển từ PENDING → CONFIRMED
- **Data consistency:** Không bị ảnh hưởng nhờ idempotency
- **System throughput:** Giảm tạm thời trong thời gian rebalance

**Best practices implemented:**
- `max.poll.records: 10` → giảm số message in-flight khi rebalance
- `session.timeout.ms: 30s` → tăng threshold để tránh false-positive rebalance do network hiccup
- `enable.auto.commit: false` → manual ACK chỉ sau khi xử lý xong → tránh mất message

---

### 18.9. Kịch bản: Network partition giữa booking-service và event-service

**Q: Nếu network giữa 2 service bị đứt (bookingsử lý được Kafka nhưng event-service không nhận được), hệ thống sẽ ra sao?**

**A:**

**Scenario:** Network partition làm event-service bị isolated (không kết nối Kafka)

**T=0:** User đặt vé thành công
- booking-service: booking created, publish `seat.status.changed` (A1: AVAILABLE→LOCKED)
- Kafka broker nhận message thành công
- event-service: **KHÔNG consume được** (network partition)

**T+10s:** User thanh toán thành công
- payment-service → booking-service: `payment.processed`
- booking-service confirm booking, publish `seat.status.changed` (A1: LOCKED→BOOKED)
- event-service: **vẫn không nhận**

**Current state:**
- **booking-service:** Booking CONFIRMED, seat lock released ✓
- **event-service DB:** Seat A1 vẫn ở trạng thái **AVAILABLE** ❌
- **event-service cache:** Nếu cache active → user khác thấy A1 available (stale data)

**T+30s:** User khác (user_B) thử đặt ghế A1
- Request → booking-service
- `CreateBookingHandler`:
  - Validate: user_B chưa có booking ✓
  - Try lock seat A1: `SET seat:lock:A1 NX EX 120`
  - **FAIL** vì lock đã được release trước đó (booking đã CONFIRMED)
  - Nhưng... domain validation **KHÔNG KIỂM TRA SEAT ĐÃ BOOKED** trong event-service
  - → **BUG:** user_B có thể đặt trùng ghế A1 ❌

**Root cause:** Dự án hiện tại **KHÔNG có validation** với event-service trước khi lock seat. Giả định network hoạt động tốt và Kafka eventually consistent.

**Proper fix (Production):**
1. **Distributed transaction với Saga pattern:**
   - CreateBookingHandler gọi **HTTP sync** sang event-service trước khi lock
   - `POST /api/v1/seats/reserve {eventId, seatIds}` 
   - event-service validate seats available và reserve
   - Nếu fail → rollback tất cả
   - Nếu success → tiếp tục lock Redis

2. **Event-service TTL cache ngắn:**
   - Cache 5 giây → sau 30s user_B sẽ query DB và thấy seat AVAILABLE (vì Kafka chưa deliver)
   - Vẫn có race condition nhưng window nhỏ hơn

3. **Kafka message retry indefinitely:**
   - event-service network khôi phục → catchup lag
   - Consume backlog messages → update seat status
   - Nếu có duplicate booking → compensating transaction: refund booking mới nhất

**Current implementation trade-off:**
- **Pros:** Fully async, low latency, không depend vào event-service availability
- **Cons:** Eventual consistency có thể lead đến inconsistency tạm thời nếu network partition

**T+5m:** Network partition resolve
- event-service reconnect Kafka
- Consumer offset vẫn giữ nguyên (committed offset trước khi partition)
- event-service consume backlog: `seat.status.changed` A1 AVAILABLE→LOCKED→BOOKED
- DB update A1: status=BOOKED
- **Eventual consistency restored**

---

### 18.10. Kịch bản: Booking expired nhưng user vẫn cố thanh toán

**Q: User đặt vé, không làm gì, sau 2 phút booking expire. User quay lại muốn thanh toán. Xử lý?**

**A:**

**T=0:** User tạo booking, expiresAt = T+120s

**T+120s:** Quartz job fire → booking EXPIRED, ghế về AVAILABLE

**T+150s (30s sau khi expire):** User quay lại, nhấn nút "Thanh toán"
- Frontend gửi request đến payment-service (giả sử có payment endpoint riêng)
- Hoặc user click Stripe payment link được gửi trong email lúc T+3s

**Scenario 1: Payment qua Stripe Checkout session:**
- User click link → redirect đến Stripe hosted page
- Stripe session có `expires_at` (mặc định 24h)
- Stripe session **vẫn valid** (chưa expire)
- User nhập thẻ, submit → Stripe charge thành công
- Stripe gọi webhook về payment-service: `checkout.session.completed`
- payment-service:
  - Load booking từ DB bằng bookingId (trong session metadata)
  - **Check status: EXPIRED** ❌
  - Không publish `payment.processed`
  - **Refund ngay:** Gọi Stripe API `refund(payment_intent)`
  - Publish `payment.refunded` → notification-service gửi email giải thích

**Scenario 2: Payment integration tốt hơn:**
- payment-service trước khi tạo Stripe session, kiểm tra booking status
- Nếu booking gần expire (còn <30s):
  - Tạo session với `expires_at` = booking.expiresAt
  - Hoặc từ chối tạo session, yêu cầu user book lại

**Scenario 3: Race condition (ít xảy ra):**
- **T+119s:** User submit payment → Stripe processing
- **T+120s:** Quartz expire booking
- **T+121s:** Stripe webhook trả về success → payment-service nhận

Xử lý:
- payment-service publish `payment.processed`
- booking-service consumer:
  - Load booking: status = EXPIRED
  - `ConfirmBookingHandler`: check `if (booking.isExpired()) throw BusinessException`
  - **KHÔNG confirm booking**
  - Publish `payment.failed` hoặc trigger refund flow
  - Manual ACK (message đã xử lý)

**Current implementation gap:**
- Dự án chưa có endpoint `GET /api/v1/bookings/{id}/payment-status` để frontend check trước khi redirect Stripe
- **Best practice:** Frontend poll booking status mỗi 10s, nếu thấy EXPIRED/CANCELLED → disable payment button

---

### 18.11. Kịch bản: Event bị cancel khi đang có nhiều booking PENDING_PAYMENT

**Q: Admin cancel event (concert hủy) trong khi có 50 bookings đang PENDING_PAYMENT. Hệ thống xử lý thế nào?**

**A:**

**Current implementation:** Dự án **CHƯA IMPLEMENT** event cancellation cascade.

**Expected workflow (nếu implement):**

**T=0:** Event có 50 bookings PENDING_PAYMENT, 30 bookings CONFIRMED

**T+1s:** Admin gọi `PUT /api/v1/events/{id}/cancel`
- event-service:
  - Update event: status = CANCELLED, cancellation_reason = "Artist sick"
  - Publish Kafka: `event.cancelled {eventId, reason}`

**T+2s:** booking-service consumer nhận `event.cancelled`
- Query DB: `SELECT * FROM bookings WHERE event_id = ? AND status IN (PENDING_PAYMENT, CONFIRMED)`
- Tìm thấy 80 bookings (50 pending + 30 confirmed)

**Xử lý từng booking:**
- **Pending bookings (50):**
  - Với mỗi booking: gọi `CancelBookingHandler` (systemInitiated=true)
  - DB update: status=CANCELLED, reason="Event cancelled by organizer"
  - Kafka publish: `seat.status.changed` (ghế về AVAILABLE – nhưng event đã cancel nên không ai book được nữa)
  - Kafka publish: `booking.cancelled`
  - Quartz: delete scheduled jobs
  - Redis: unlock seats

- **Confirmed bookings (30):**
  - **KHÔNG thể cancel đơn giản** → cần refund flow
  - Với mỗi booking:
    - DB update: status=REFUND_PENDING
    - Gọi payment-service: `POST /api/v1/payments/{transactionId}/refund`
    - payment-service gọi Stripe API: `refund()`
    - Stripe success → DB: status=REFUNDED
    - Kafka publish: `payment.refunded`

**T+10s:** notification-service consumer
- Nhận 80 events: `booking.cancelled`, `payment.refunded`
- Gửi 80 emails với template khác nhau:
  - Pending: "Event đã bị hủy, booking của bạn tự động hủy"
  - Confirmed: "Event bị hủy, tiền đã được hoàn lại vào thẻ trong 5-7 ngày"

**Challenges:**
1. **Batch processing:** 80 bookings → 80 Stripe refund API calls → có thể timeout
   - **Solution:** Queue-based processing: publish `refund.requested` → dedicated refund-worker consume
2. **Partial failure:** 25/30 refunds thành công, 5 fail do Stripe API down
   - **Solution:** Retry queue + DLT, manual intervention cho failed refunds
3. **Idempotency:** Nếu `event.cancelled` message bị retry
   - **Solution:** Check `booking.status IN (CANCELLED, REFUNDED)` → skip

**Current gap:** Event cancellation chưa implement → đây là feature cần bổ sung cho production.

---

### 18.12. Kịch bản: SSE connection bị drop giữa chừng

**Q: User subscribe SSE để theo dõi booking, nhưng connection bị drop (network hiccup, tab background, mobile sleep). Xử lý?**

**A:**

**T=0:** User tạo booking, subscribe SSE
- Connection establish: `GET /api/v1/bookings/{id}/stream`
- booking-service: `emitters.put(bookingId, sseEmitter)`
- Send initial state

**T+30s:** Heartbeat sent successfully

**T+60s:** User phone screen off → mobile OS suspend network → **connection drop**
- TCP connection close (FIN packet từ client)
- booking-service: `SseEmitter.onError()` callback triggered
- `emitters.remove(bookingId)` → emitter bị xóa khỏi registry

**T+65s:** Payment thành công
- booking-service `ConfirmBookingHandler` cố push SSE:
  - `emitter = emitters.get(bookingId)` → **NULL**
  - Code check: `if (emitter != null) { send ... }`
  - Không send được → user không nhận realtime update

**T+70s:** User wake screen, mở app lại
- App code cần **auto-reconnect** SSE:
  - Send `GET /api/v1/bookings/{id}/stream` lại
  - booking-service: validate ownership, tạo emitter mới
  - Send initial state với status **hiện tại** (đã CONFIRMED)
  - User nhận update dù bị disconnect giữa chừng

**Current implementation:**
- Server-side: có `onError` cleanup ✓
- Client-side: **CHƯA IMPLEMENT** auto-reconnect
- → Gap: Cần frontend code:

```javascript
const evtSource = new EventSource('/api/v1/bookings/123/stream');
evtSource.onerror = () => {
  setTimeout(() => {
    evtSource.close();
    // Recreate connection
    connectSSE(bookingId);
  }, 3000); // retry after 3s
};
```

**Alternative: Polling fallback:**
- Nếu SSE không stable (corporate firewall, old browser):
  - Client poll `GET /api/v1/bookings/{id}` mỗi 5s
  - Kém real-time hơn nhưng reliable hơn

**Best practice:**
- SSE timeout: 5 phút (hiện tại) → hợp lý
- Heartbeat: 30s → giữ connection alive qua NAT/proxy
- Client auto-reconnect với exponential backoff: 1s, 2s, 4s, 8s, max 30s

---

### 18.13. Kịch bản: Multi-region deployment latency

**Q: Hệ thống deploy ở 2 region (US-East, EU-West), user ở EU book vé. Cross-region latency ảnh hưởng thế nào?**

**A:**

**Setup giả định:**
- Kafka cluster: US-East (single region)
- booking-service: 2 instances US-East, 2 instances EU-West
- payment-service: US-East only
- PostgreSQL: US-East primary, EU-West read replica (replication lag 50-100ms)
- Redis: cluster ở cả 2 regions (replication lag 20-30ms)

**T=0:** User ở London gọi API (routing đến EU-West)
- API Gateway (EU-West) → booking-service (EU-West instance)
- `CreateBookingHandler`:
  - **Step 1: Redis lock** (EU-West Redis)
    - Latency: ~5ms local
    - Lock thành công
  - **Step 2: DB write** (write đến US-East primary)
    - Latency: ~80ms cross-Atlantic
    - INSERT booking
  - **Step 3: Publish Kafka** (US-East Kafka broker)
    - Latency: ~80ms
    - Acks=all → wait for ISR replication: +20ms
    - Total Kafka publish: ~100ms
  - **Step 4: Quartz schedule** (local scheduler)
    - Write QRTZ tables vào US-East DB: ~80ms

**Total create booking latency:** ~5 + 80 + 100 + 80 = **~265ms**

**T+100ms:** Kafka message `booking.created` arrive US-East
- payment-service (US-East) consume
- Gọi Stripe API (US-based): latency ~200ms
- Total payment processing: ~300-500ms

**T+400ms:** Kafka `payment.processed` publish
- booking-service EU-West instance consume:
  - Consumer poll từ US-East Kafka: extra ~80ms latency per poll cycle
  - Process message, update DB (US-East): ~80ms
  - Confirm booking

**T+500ms:** SSE push
- booking-service EU-West có emitter của user (in-memory)
- Send qua existing TCP connection: ~5ms local
- User nhận notification

**Total end-to-end: ~500-700ms** (so với single region: ~200-300ms)

**Challenges:**
1. **Redis cross-region race:**
   - EU-West user và US-East user cùng lock ghế A1
   - Nếu lock ở 2 Redis clusters khác nhau → có thể double-lock
   - **Solution:** Redis cross-region replication với CRDT (Conflict-free Replicated Data Type) hoặc single Redis cluster với multi-region nodes

2. **DB replication lag:**
   - User ở EU đọc từ read replica → có thể thấy stale data (booking chưa replicate)
   - **Solution:** Write đến primary, read từ primary cho critical paths

3. **Kafka cross-region:**
   - Message có thể delay 100-200ms
   - **Solution:** Deploy Kafka cluster stretched across regions với rack awareness

**Best practice:**
- **Active-Active multi-region:** Database sharding theo region (EU bookings → EU DB)
- **Geo-distributed Kafka:** Kafka MirrorMaker 2 để replicate topics
- **Regional failover:** Nếu US-East down, EU-West takeover

---

**Q: Tổng kết các failure modes và recovery strategies?**

**A:**

| Failure | Detection | Impact | Recovery | MTTD | MTTR |
|---------|-----------|--------|----------|------|------|
| Redis down | Prometheus alert | Không đặt vé được, cache miss | Failover Redis replica | <1m | 2-5m |
| PostgreSQL primary down | Connection errors | Service outage | Promote replica to primary | <30s | 5-10m |
| Kafka broker down | Consumer lag spike | Message delay | Kafka partition rebalance | <1m | Auto |
| booking-service crash | Health check fail | Partial outage | Kubernetes restart pod | <30s | 1-2m |
| payment-service crash | Kafka consumer lag | Payment delay | K8s restart, Kafka catchup | <1m | 2-3m |
| Network partition | Timeout alerts | Split-brain risk | Network team fix | 1-5m | 5-30m |
| Stripe API down | Circuit breaker OPEN | Cannot process payment | Wait for Stripe recovery | <1m | External |
| Full data center outage | All services down | Complete outage | Failover to backup DC | <5m | 30m-2h |

**MTTD:** Mean Time To Detect
**MTTR:** Mean Time To Recover

---

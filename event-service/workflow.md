# Event Service – Workflow End-to-End

> **Mục đích tài liệu:** Mô tả toàn bộ luồng hoạt động của `event-service` từ đầu đến cuối, bao gồm từng file ở từng layer đang làm gì, gọi cái gì, và vì sao. Đọc xong tài liệu này bạn sẽ biết chính xác một request đi qua những file nào theo thứ tự nào.

---

## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Sơ đồ layer và file](#2-sơ-đồ-layer-và-file)
3. [Workflow 1 – Tạo Event mới (Admin)](#3-workflow-1--tạo-event-mới-admin)
4. [Workflow 2 – Cập nhật Event (Admin)](#4-workflow-2--cập-nhật-event-admin)
5. [Workflow 3 – Tìm kiếm Events (Public)](#5-workflow-3--tìm-kiếm-events-public)
6. [Workflow 4 – Xem chi tiết Event (Public)](#6-workflow-4--xem-chi-tiết-event-public)
7. [Workflow 5 – Xem Seat Map (Public, High Traffic)](#7-workflow-5--xem-seat-map-public-high-traffic)
8. [Workflow 6 – Cập nhật trạng thái Ghế qua Kafka](#8-workflow-6--cập-nhật-trạng-thái-ghế-qua-kafka)
9. [Workflow 7 – Xem Venue](#9-workflow-7--xem-venue)
10. [State Machine: Event Status](#10-state-machine-event-status)
11. [State Machine: Seat Status](#11-state-machine-seat-status)
12. [Chiến lược Cache Redis](#12-chiến-lược-cache-redis)
13. [Cấu hình hệ thống và Infrastructure](#13-cấu-hình-hệ-thống-và-infrastructure)
14. [Sơ đồ Database Schema](#14-sơ-đồ-database-schema)
15. [Tóm tắt quan hệ giữa các file](#15-tóm-tắt-quan-hệ-giữa-các-file)

---

## 1. Tổng quan kiến trúc

`event-service` là một **microservice** thuộc hệ thống Ticketmaster. Nó chịu trách nhiệm:

| Trách nhiệm | Mô tả |
|---|---|
| Quản lý Venues | Địa điểm tổ chức sự kiện (sân vận động, nhà hát...) |
| Quản lý Events | Tạo, cập nhật, publish, cancel sự kiện |
| Tìm kiếm Events | Tìm theo keyword, thành phố, thể loại, có pagination |
| Seat Map | Hiển thị bản đồ ghế ngồi theo khu vực, real-time |
| Sync trạng thái ghế | Lắng nghe Kafka topic từ `booking-service` để cập nhật ghế |

**Các thành phần bên ngoài mà service này kết nối:**

```
Client (Browser/App)
        │  HTTP REST
        ▼
[ API Gateway / Service Registry (Eureka) ]
        │
        ▼
  event-service (:8082)
        │
        ├──► PostgreSQL (event_db)   ← lưu trữ chính
        ├──► Redis                   ← cache seat map & event info
        └──► Kafka (Consumer)        ← nhận seat.status.changed từ booking-service
```

**Kiến trúc nội bộ:** Dự án áp dụng **Domain-Driven Design (DDD)** với phân tách 4 layer rõ ràng:

```
interfaces/     ← Tầng ngoài cùng: nhận HTTP request từ client
application/    ← Tầng điều phối: xử lý use case
domain/         ← Tầng nghiệp vụ thuần túy: business logic
infrastructure/ ← Tầng kỹ thuật: DB, Redis, Kafka config
```

---

## 2. Sơ đồ layer và file

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  INTERFACES LAYER  (nhận request từ bên ngoài, trả response)                   │
│                                                                                 │
│  rest/                                                                          │
│    EventController.java   ← /api/v1/events  (search, getById, create, update)  │
│    SeatController.java    ← /api/v1/seats   (getSeatMap)                       │
│    VenueController.java   ← /api/v1/venues  (getById, listByCity)              │
│                                                                                 │
│  dto/  (các class chỉ để truyền dữ liệu qua API, không có logic)               │
│    CreateEventRequest.java   ← dữ liệu đầu vào khi tạo event                  │
│    UpdateEventRequest.java   ← dữ liệu đầu vào khi cập nhật event             │
│    EventSearchRequest.java   ← query params để tìm kiếm                        │
│    EventResponse.java        ← dữ liệu trả về cho event                       │
│    SeatMapResponse.java      ← dữ liệu trả về cho seat map                    │
│    SeatResponse.java         ← dữ liệu trả về cho từng ghế                    │
│    VenueResponse.java        ← dữ liệu trả về cho venue                       │
└───────────────────────────────────┬─────────────────────────────────────────────┘
                                    │ gọi xuống
┌───────────────────────────────────▼─────────────────────────────────────────────┐
│  APPLICATION LAYER  (điều phối use case, không chứa business logic)            │
│                                                                                 │
│  handler/   (mỗi handler = 1 use case)                                         │
│    CreateEventHandler.java   ← use case: tạo event                             │
│    UpdateEventHandler.java   ← use case: cập nhật event                        │
│    SearchEventsHandler.java  ← use case: tìm kiếm events                       │
│    GetSeatMapHandler.java    ← use case: lấy seat map (với cache)              │
│                                                                                 │
│  command/   (dữ liệu đầu vào cho các handler ghi)                              │
│    CreateEventCommand.java                                                      │
│    UpdateEventCommand.java                                                      │
│    UpdateSeatStatusCommand.java                                                 │
│                                                                                 │
│  query/     (dữ liệu đầu vào cho các handler đọc)                              │
│    SearchEventsQuery.java                                                       │
│    GetSeatMapQuery.java                                                         │
│                                                                                 │
│  kafka/     (nhận message bất đồng bộ từ Kafka)                                │
│    SeatStatusEventConsumer.java  ← consumer topic seat.status.changed          │
└───────────────────────────────────┬─────────────────────────────────────────────┘
                                    │ gọi xuống
┌───────────────────────────────────▼─────────────────────────────────────────────┐
│  DOMAIN LAYER  (business logic thuần, không import Spring/JPA)                 │
│                                                                                 │
│  model/   (các đối tượng nghiệp vụ - Pure Java)                                │
│    Event.java        ← Aggregate Root: sự kiện                                 │
│    Venue.java        ← Aggregate Root: địa điểm                                │
│    Seat.java         ← Entity: ghế ngồi cụ thể                                 │
│    SeatSection.java  ← Entity: khu vực ghế (VIP, Khu A...)                    │
│    SeatStatus.java   ← Enum: AVAILABLE | LOCKED | BOOKED                       │
│                                                                                 │
│  service/                                                                       │
│    EventDomainService.java  ← business logic cần phối hợp nhiều aggregate      │
│                                                                                 │
│  repository/  (chỉ là interface, không có code thực thi)                       │
│    EventRepository.java                                                         │
│    SeatRepository.java                                                          │
│    VenueRepository.java                                                         │
└───────────────────────────────────┬─────────────────────────────────────────────┘
                                    │ implement bởi
┌───────────────────────────────────▼─────────────────────────────────────────────┐
│  INFRASTRUCTURE LAYER  (kỹ thuật: DB, Redis, Kafka)                            │
│                                                                                 │
│  persistence/                                                                   │
│    entity/                                                                      │
│      EventJpaEntity.java        ← mapping tới bảng "events" (PostgreSQL)       │
│      VenueJpaEntity.java        ← mapping tới bảng "venues"                    │
│      SeatJpaEntity.java         ← mapping tới bảng "seats"                     │
│      SeatSectionJpaEntity.java  ← mapping tới bảng "seat_sections"             │
│    mapper/  (MapStruct: tự generate code convert giữa domain ↔ JPA ↔ DTO)     │
│      EventMapper.java                                                           │
│      SeatMapper.java                                                            │
│      VenueMapper.java                                                           │
│    repository/  (implement domain repository interface)                         │
│      EventJpaRepository.java   ← implements EventRepository (domain)           │
│      SeatJpaRepository.java    ← implements SeatRepository (domain)            │
│      VenueJpaRepository.java   ← implements VenueRepository (domain)           │
│                                                                                 │
│  cache/                                                                         │
│    EventCacheService.java  ← Redis cache cho event info (TTL 10 phút)         │
│    SeatCacheService.java   ← Redis cache cho seat map (TTL 5 giây)            │
│                                                                                 │
│  config/                                                                        │
│    CacheConfig.java    ← cấu hình RedisTemplate (key=String, value=JSON)       │
│    KafkaConfig.java    ← cấu hình consumer/producer, DLQ, retry                │
│    SwaggerConfig.java  ← cấu hình OpenAPI docs                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Workflow 1 – Tạo Event mới (Admin)

**Endpoint:** `POST /api/v1/events`
**Yêu cầu:** Header `X-User-Id` (Admin đã xác thực từ API Gateway)

```
Client
  │
  │  POST /api/v1/events
  │  Body: { name, description, venueId, startTime, endTime, category, imageUrl }
  │  Header: X-User-Id: admin-001
  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INTERFACES] EventController.java                                            │
│                                                                              │
│  @PostMapping → createEvent()                                                │
│  1. @Valid kiểm tra CreateEventRequest:                                      │
│       - name: không rỗng, tối đa 255 ký tự                                  │
│       - venueId: không rỗng                                                  │
│       - startTime: bắt buộc, phải là tương lai (@Future)                    │
│       - endTime: bắt buộc                                                    │
│       - category: không rỗng                                                 │
│  2. Nếu validation fail → Spring trả lỗi 400 Bad Request tự động            │
│  3. Map CreateEventRequest → CreateEventCommand (builder pattern)            │
│  4. Gọi createEventHandler.handle(command)                                   │
│  5. Trả về 201 Created + ApiResponse<EventResponse>                         │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │ createEventHandler.handle(command)
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [APPLICATION] CreateEventHandler.java                                        │
│                                                                              │
│  @Transactional – toàn bộ xử lý trong 1 transaction DB                      │
│                                                                              │
│  Bước 1: Validate venue tồn tại                                              │
│    → gọi eventDomainService.validateVenueExists(venueId)                    │
│       → bên trong gọi venueRepository.existsById(venueId)                   │
│       → nếu không tồn tại → ném ResourceNotFoundException → trả 404         │
│                                                                              │
│  Bước 2: Lấy thông tin Venue (cần capacity để set totalSeats)               │
│    → gọi venueRepository.findById(venueId)                                  │
│       → trả về domain object Venue (có field: id, name, capacity, ...)      │
│                                                                              │
│  Bước 3: Tạo Event aggregate                                                 │
│    → gọi Event.create(id, name, description, venueId, venueName,            │
│                       startTime, endTime, category, imageUrl, capacity)      │
│       → Factory method trong domain model, trả về Event với:                │
│          - id: UUID mới (IdGenerator.newId())                                │
│          - status = "DRAFT"  ← luôn bắt đầu là DRAFT                       │
│          - totalSeats = capacity của venue                                   │
│          - availableSeats = capacity (ban đầu tất cả ghế đều trống)         │
│          - createdAt = updatedAt = Instant.now()                             │
│                                                                              │
│  Bước 4: Lưu Event vào DB                                                   │
│    → gọi eventRepository.save(event)                                         │
│                                                                              │
│  Bước 5: Map domain Event → EventResponse DTO                               │
│    → gọi eventMapper.toResponse(savedEvent)                                  │
│    → trả về EventResponse cho Controller                                     │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │
          ┌───────────┼──────────────────┐
          │           │                  │
          ▼           ▼                  ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────────────────────┐
│[DOMAIN]      │ │[DOMAIN]      │ │[INFRA] EventJpaRepository.java   │
│EventDomain   │ │VenueRepo     │ │                                  │
│Service.java  │ │(interface)   │ │ save(event):                     │
│              │ │              │ │  1. eventMapper.toEntity(event)  │
│validateVenue │ │findById()    │ │     → Event → EventJpaEntity     │
│Exists():     │ │  ↓           │ │  2. springDataRepository.save()  │
│  venueRepo   │ │[INFRA]       │ │     → JPA → INSERT INTO events   │
│  .existsById │ │VenueJpaRepo  │ │  3. eventMapper.toDomain(entity) │
│              │ │.findById()   │ │     → EventJpaEntity → Event     │
└──────────────┘ └──────────────┘ └──────────────────────────────────┘
```

**Kết quả trả về client:**
```json
HTTP 201 Created
{
  "success": true,
  "data": {
    "id": "evt-uuid-001",
    "name": "BLACKPINK World Tour - Hà Nội",
    "status": "DRAFT",
    "venueId": "venue-uuid-001",
    "venueName": "Sân vận động Mỹ Đình",
    "totalSeats": 40000,
    "availableSeats": 40000,
    "bookable": false
  }
}
```

> **Lưu ý:** Event mới tạo luôn có status `DRAFT`. Muốn bán vé phải gọi Update Event để chuyển sang `PUBLISHED`.

---

## 4. Workflow 2 – Cập nhật Event (Admin)

**Endpoint:** `PUT /api/v1/events/{id}`
**Yêu cầu:** Header `X-User-Id` (Admin)

```
Client
  │
  │  PUT /api/v1/events/evt-001
  │  Body: { status: "PUBLISHED" }  ← hoặc các fields khác như name, startTime...
  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INTERFACES] EventController.java                                            │
│                                                                              │
│  @PutMapping("/{id}") → updateEvent()                                       │
│  1. @Valid kiểm tra UpdateEventRequest:                                      │
│       - name: tối đa 255 ký tự (optional)                                   │
│       - status: nếu có, chỉ được là "PUBLISHED" hoặc "CANCELLED" (@Pattern) │
│  2. Map UpdateEventRequest → UpdateEventCommand (gán thêm eventId từ path)  │
│  3. Gọi updateEventHandler.handle(command)                                   │
│  4. Trả về 200 OK + "Event updated successfully"                            │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │ updateEventHandler.handle(command)
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [APPLICATION] UpdateEventHandler.java                                        │
│                                                                              │
│  @Transactional                                                              │
│                                                                              │
│  Bước 1: Tìm Event theo ID                                                  │
│    → eventRepository.findById(eventId)                                       │
│    → nếu không tìm thấy → 404 Not Found                                     │
│                                                                              │
│  Bước 2: Xử lý chuyển đổi Status (nếu command có status)                   │
│    → Nếu status = "PUBLISHED":                                               │
│         gọi event.publish()                                                  │
│           → Domain method kiểm tra: chỉ DRAFT mới được publish              │
│           → Nếu không phải DRAFT → ném BusinessException → 409 Conflict     │
│           → Trả về Event mới với status = "PUBLISHED"                        │
│    → Nếu status = "CANCELLED":                                               │
│         gọi event.cancel()                                                   │
│           → Domain method kiểm tra: không cancel event đã CANCELLED         │
│           → Trả về Event mới với status = "CANCELLED"                        │
│                                                                              │
│  Bước 3: Cập nhật các field thông tin (nếu command có giá trị)              │
│    → Nếu command.name != null:                                               │
│         Build lại Event object với các giá trị mới (name, description,      │
│         startTime, endTime, category, imageUrl)                              │
│                                                                              │
│  Bước 4: Lưu Event đã cập nhật                                              │
│    → eventRepository.save(updatedEvent)                                      │
│    → JPA thực hiện UPDATE events SET ... WHERE id = ?                        │
│                                                                              │
│  Bước 5: Xóa cache để lần đọc tiếp theo lấy data mới từ DB                 │
│    → eventCacheService.evictEventCache(eventId)                              │
│    → Redis DEL "event:info:{eventId}"                                        │
│                                                                              │
│  Bước 6: Trả về EventResponse                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Ví dụ về trường hợp lỗi:**
- Admin cố publish event đã PUBLISHED → domain `event.publish()` throw `BusinessException("Only DRAFT events can be published")` → 409 Conflict
- Admin cố cancel event đã CANCELLED → `event.cancel()` throw `BusinessException("Event is already cancelled")` → 409 Conflict

---

## 5. Workflow 3 – Tìm kiếm Events (Public)

**Endpoint:** `GET /api/v1/events/search?keyword=blackpink&city=hanoi&category=CONCERT&page=0&size=20`

```
Client
  │
  │  GET /api/v1/events/search?keyword=blackpink&city=hanoi&category=CONCERT
  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INTERFACES] EventController.java                                            │
│                                                                              │
│  @GetMapping("/search") → searchEvents()                                     │
│  1. @ModelAttribute bind query params → EventSearchRequest:                 │
│       keyword = "blackpink"                                                  │
│       city    = "hanoi"                                                      │
│       category = "CONCERT"                                                   │
│       page = 0, size = 20 (default)                                          │
│       sortBy = "startTime", sortDir = "asc" (default)                       │
│                                                                              │
│  2. Tạo Sort object từ sortBy + sortDir                                      │
│  3. Tạo SearchEventsQuery:                                                   │
│       - status cứng = "PUBLISHED" (API public chỉ trả events đang bán vé)  │
│       - size bị cap tối đa 100 (tránh over-fetching)                        │
│       - pageable = PageRequest.of(page, size, sort)                          │
│  4. Gọi searchEventsHandler.handle(query)                                    │
│  5. Trả về PageResponse<EventResponse> với metadata pagination               │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │ searchEventsHandler.handle(query)
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [APPLICATION] SearchEventsHandler.java                                       │
│                                                                              │
│  @Transactional(readOnly = true)                                             │
│                                                                              │
│  Bước 1: Gọi eventRepository.search(keyword, city, category, status, page) │
│  Bước 2: Map từng EventJpaEntity → Event → EventResponse                   │
│  Bước 3: Wrap trong PageResponse (có totalElements, totalPages, ...)        │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │ eventRepository.search(...)
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INFRA] EventJpaRepository.java                                              │
│                                                                              │
│  Delegate xuống SpringDataEventRepository.search() với JPQL:                │
│                                                                              │
│  SELECT e FROM EventJpaEntity e                                              │
│  WHERE (:status   IS NULL OR e.status   = :status)                          │
│    AND (:category IS NULL OR e.category = :category)                         │
│    AND (:city     IS NULL OR e.venueName LIKE %:city%)                       │
│    AND (:keyword  IS NULL OR e.name LIKE %:keyword%                          │
│                           OR e.description LIKE %:keyword%)                  │
│  ORDER BY e.startTime ASC                                                    │
│                                                                              │
│  → PostgreSQL query với index: idx_events_category_status                    │
│  → Nếu param là NULL → filter đó bị bỏ qua tự động (dynamic query)         │
│  → Kết quả paged: trả về Page<EventJpaEntity>                               │
│  → Map: EventJpaEntity → Event (domain) → EventResponse (DTO)               │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Kết quả trả về:**
```json
{
  "success": true,
  "data": {
    "content": [
      { "id": "evt-001", "name": "BLACKPINK World Tour", "status": "PUBLISHED", ... }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1,
    "last": true
  }
}
```

---

## 6. Workflow 4 – Xem chi tiết Event (Public)

**Endpoint:** `GET /api/v1/events/{id}`
**Đặc điểm:** Dùng Redis cache để giảm load DB (TTL 10 phút).

```
Client
  │
  │  GET /api/v1/events/evt-001
  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INTERFACES] EventController.java                                            │
│                                                                              │
│  @GetMapping("/{id}") → getEvent()                                           │
│                                                                              │
│  ┌─ Bước 1: Kiểm tra Redis Cache ──────────────────────────────────────┐   │
│  │   gọi eventCacheService.getEvent(id)                                 │   │
│  │   → Redis GET "event:info:evt-001"                                   │   │
│  │                                                                       │   │
│  │   CACHE HIT:  → trả về EventResponse luôn, không cần vào DB         │   │
│  │   CACHE MISS: → tiếp tục bước 2                                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Bước 2 (chỉ khi cache miss): Query DB                                      │
│    → eventRepository.findById(id)                                            │
│    → nếu không tìm thấy → 404 Not Found                                     │
│    → eventMapper.toResponse(event)                                           │
│                                                                              │
│  Bước 3: Lưu kết quả vào cache để lần sau nhanh hơn                        │
│    → eventCacheService.cacheEvent(id, response)                              │
│    → Redis SET "event:info:evt-001" {json} EX 600  (10 phút)               │
│                                                                              │
│  Bước 4: Trả về 200 OK + EventResponse                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Cache key:** `event:info:{eventId}` | **TTL:** 10 phút
**Cache bị xóa khi:** Admin cập nhật event (UpdateEventHandler gọi `evictEventCache`)

---

## 7. Workflow 5 – Xem Seat Map (Public, High Traffic)

**Endpoint:** `GET /api/v1/seats/event/{eventId}?useCache=true`
**Đây là endpoint có traffic cao nhất** – khi event hot (BLACKPINK, BTS), hàng nghìn user đồng thời xem seat map.

```
Client
  │
  │  GET /api/v1/seats/event/evt-001?useCache=true
  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INTERFACES] SeatController.java                                             │
│                                                                              │
│  @GetMapping("/event/{eventId}") → getSeatMap()                              │
│  1. Tạo GetSeatMapQuery:                                                     │
│       eventId = "evt-001"                                                    │
│       useCache = true (default)                                              │
│  2. Gọi getSeatMapHandler.handle(query)                                      │
│  3. Trả về 200 OK + ApiResponse<SeatMapResponse>                            │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │ getSeatMapHandler.handle(query)
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [APPLICATION] GetSeatMapHandler.java                                         │
│                                                                              │
│  @Transactional(readOnly = true)                                             │
│                                                                              │
│  Bước 1: Validate event tồn tại                                             │
│    → eventRepository.findById(eventId)                                       │
│    → nếu không tìm thấy → 404 Not Found                                     │
│                                                                              │
│  ┌─ Bước 2: Kiểm tra Redis Cache (chỉ khi useCache=true) ───────────────┐  │
│  │   gọi seatCacheService.getSeatMap(eventId)                            │  │
│  │   → Redis GET "event:seatmap:evt-001"                                 │  │
│  │                                                                        │  │
│  │   CACHE HIT  → return cached SeatMapResponse ngay lập tức            │  │
│  │               (không xuống DB, tiết kiệm 99% DB load)                │  │
│  │   CACHE MISS → tiếp tục bước 3                                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  Bước 3 (chỉ khi cache miss): Query DB                                      │
│    → seatRepository.findByEventId(eventId)                                   │
│    → SELECT * FROM seats WHERE event_id = 'evt-001'                         │
│    → Trả về List<Seat> (có thể hàng chục nghìn ghế)                        │
│                                                                              │
│  Bước 4: Nhóm ghế theo sectionId                                            │
│    → Map<sectionId, List<SeatResponse>>                                      │
│    → Dùng Java Stream groupingBy(Seat::getSectionId)                         │
│    → Mỗi SeatResponse có: id, row, number, price, status, displayLabel      │
│    → displayLabel được tính: row + "-" + number (vd: "A-5")                 │
│    → available = (status == AVAILABLE) → frontend tô màu xanh/vàng/đỏ      │
│                                                                              │
│  Bước 5: Build SeatMapResponse                                              │
│    {                                                                          │
│      eventId: "evt-001",                                                     │
│      eventName: "BLACKPINK World Tour",                                      │
│      totalSeats: 40000,                                                      │
│      availableSeats: 35000,                                                  │
│      seatsBySection: {                                                        │
│        "section-vip-id": [ {id, row, number, status: "AVAILABLE", ...}, ], │
│        "section-a-id":   [ {id, row, number, status: "BOOKED", ...}, ... ]  │
│      }                                                                        │
│    }                                                                          │
│                                                                              │
│  Bước 6: Lưu vào cache                                                      │
│    → seatCacheService.cacheSeatMap(eventId, response)                        │
│    → Redis SET "event:seatmap:evt-001" {json} EX 5  (5 GIÂY)               │
│    → TTL rất ngắn: đảm bảo không quá 5 giây user nhìn thấy data cũ          │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Tại sao TTL chỉ 5 giây?**
- Khi có 10.000 user đang xem seat map, không cần tất cả đều thấy trạng thái ghế realtime 100%.
- TTL 5 giây đủ để 1 ghế được giữ bởi booking chưa hiển thị ngay, nhưng không gây double-booking (booking-service có Redisson distributed lock riêng).
- Sau 5 giây cache tự expire, lần query tiếp theo warm cache từ DB mới nhất.

**Kết quả trả về:**
```json
{
  "eventId": "evt-001",
  "eventName": "BLACKPINK World Tour",
  "totalSeats": 500,
  "availableSeats": 237,
  "seatsBySection": {
    "section-vip-id": [
      { "id": "seat-1", "row": "A", "number": "1", "status": "AVAILABLE", "displayLabel": "A-1", "available": true },
      { "id": "seat-2", "row": "A", "number": "2", "status": "BOOKED",    "displayLabel": "A-2", "available": false }
    ],
    "section-a-id": [ ... ]
  }
}
```

---

## 8. Workflow 6 – Cập nhật trạng thái Ghế qua Kafka

**Trigger:** `booking-service` publish message vào Kafka topic `seat.status.changed`
**Đây là luồng bất đồng bộ** – không có HTTP request từ client.

**Khi nào booking-service publish event này?**
- User chọn ghế → `AVAILABLE → LOCKED`
- Payment thành công → `LOCKED → BOOKED`
- Payment thất bại / timeout → `LOCKED → AVAILABLE`
- User cancel booking → `BOOKED → AVAILABLE`

```
booking-service
  │
  │  PUBLISH to Kafka topic: "seat.status.changed"
  │  Message: {
  │    eventId: "kafka-msg-uuid",
  │    eventShowId: "evt-001",
  │    seatId: "seat-123",
  │    previousStatus: "AVAILABLE",
  │    newStatus: "LOCKED"
  │  }
  ▼
[ Kafka Broker ]
  │  partition by seatId/eventId (6 partitions)
  │
  ▼  3 consumer threads (concurrency=3 trong KafkaConfig)
┌──────────────────────────────────────────────────────────────────────────────┐
│ [APPLICATION] SeatStatusEventConsumer.java                                   │
│                                                                              │
│  @KafkaListener(topics = "seat.status.changed",                              │
│                 groupId = "event-service-group")                             │
│  @Transactional                                                              │
│                                                                              │
│  consume(SeatStatusChangedEvent event, Acknowledgment ack):                 │
│                                                                              │
│  Log: "[KAFKA] Received | seatId=seat-123 AVAILABLE → LOCKED"               │
│                                                                              │
│  try {                                                                        │
│    Bước 1: Cập nhật DB                                                      │
│      → gọi eventDomainService.updateSeatStatus(seatId, newStatus)           │
│                                                                              │
│    Bước 2: Invalidate cache                                                  │
│      → gọi seatCacheService.evictSeatMap(eventShowId)                        │
│      → Redis DEL "event:seatmap:evt-001"                                    │
│      → Lần query tiếp theo sẽ lấy data mới từ DB                           │
│                                                                              │
│    Bước 3: Acknowledge message (chỉ sau khi thành công)                     │
│      → acknowledgment.acknowledge()                                          │
│      → Kafka commit offset → message không bị reprocess                     │
│  }                                                                            │
│  catch (Exception ex) {                                                       │
│    → KHÔNG acknowledge                                                       │
│    → Kafka retry sau 1 giây                                                  │
│    → Sau 3 lần retry → message vào DLQ: "seat.status.changed.DLT"          │
│    → Alert/monitor DLQ để xử lý thủ công nếu cần                           │
│  }                                                                            │
└─────────────────────┬────────────────────────────────────────────────────────┘
                      │ eventDomainService.updateSeatStatus(seatId, newStatus)
                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [DOMAIN] EventDomainService.java                                             │
│                                                                              │
│  updateSeatStatus(seatId, newStatus):                                        │
│                                                                              │
│  Bước 1: Tìm Seat theo ID                                                   │
│    → seatRepository.findById(seatId)                                         │
│    → nếu không tìm thấy → ném ResourceNotFoundException                     │
│                                                                              │
│  Bước 2: Cập nhật trạng thái ghế (domain method)                           │
│    → seat.withStatus(newStatus)                                              │
│    → Tạo Seat object mới với status = SeatStatus.valueOf(newStatus)         │
│       (AVAILABLE | LOCKED | BOOKED)                                          │
│    → seatRepository.save(updatedSeat)                                        │
│    → UPDATE seats SET status = 'LOCKED' WHERE id = 'seat-123'               │
│                                                                              │
│  Bước 3: Đếm lại ghế trống của event                                        │
│    → seatRepository.countByEventIdAndStatus(eventId, AVAILABLE)             │
│    → SELECT COUNT(*) FROM seats                                              │
│         WHERE event_id = 'evt-001' AND status = 'AVAILABLE'                 │
│    → Kết quả: availableCount = 35999                                         │
│                                                                              │
│  Bước 4: Cập nhật availableSeats trong Event                                │
│    → eventRepository.findById(eventId)                                       │
│    → event.updateAvailableSeats(availableCount)                              │
│       → Tạo Event mới với availableSeats = 35999                            │
│    → eventRepository.save(updatedEvent)                                      │
│    → UPDATE events SET available_seats = 35999 WHERE id = 'evt-001'         │
│                                                                              │
│  Log: "[DOMAIN] seat-123 → LOCKED | evt-001 availableSeats=35999"           │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Idempotency:** Nếu cùng 1 Kafka message được deliver 2 lần (Kafka at-least-once), việc set `status = 'LOCKED'` 2 lần không gây hại vì trạng thái giống nhau. Việc đếm lại ghế trống cũng idempotent.

**DLQ flow:**
```
Kafka retry 1 (sau 1s) → fail
Kafka retry 2 (sau 1s) → fail
Kafka retry 3 (sau 1s) → fail
→ Gửi message vào topic: "seat.status.changed.DLT"
→ Alert team DevOps để debug
```

---

## 9. Workflow 7 – Xem Venue

**Endpoint:** `GET /api/v1/venues/{id}` hoặc `GET /api/v1/venues?city=hanoi`

```
Client
  │
  │  GET /api/v1/venues/venue-001
  ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ [INTERFACES] VenueController.java                                            │
│                                                                              │
│  @GetMapping("/{id}") → getVenue()                                           │
│                                                                              │
│  1. venueRepository.findById(id)                                             │
│     → VenueJpaRepository → SpringDataVenueRepository.findById()             │
│     → SELECT * FROM venues WHERE id = 'venue-001'                           │
│     → Map VenueJpaEntity → Venue (domain) → VenueResponse                  │
│     → nếu không tìm thấy → 404 Not Found                                    │
│  2. Trả về 200 OK + VenueResponse                                           │
│                                                                              │
│  @GetMapping → getVenues(?city=hanoi)                                        │
│                                                                              │
│  1. venueRepository.findByCity("hanoi")                                      │
│     → VenueJpaRepository → SpringDataVenueRepository.findByCityIgnoreCase() │
│     → SELECT * FROM venues WHERE LOWER(city) = LOWER('hanoi')               │
│  2. Trả về List<VenueResponse>                                               │
│                                                                              │
│  Lưu ý: VenueController là controller đơn giản nhất – không có cache        │
│  và không đi qua Handler, trực tiếp dùng Repository.                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Kết quả:**
```json
{
  "id": "venue-001",
  "name": "Sân vận động Mỹ Đình",
  "address": "Đường Lê Đức Thọ, Mỹ Đình",
  "city": "Hà Nội",
  "country": "Vietnam",
  "capacity": 40000,
  "displayAddress": "Đường Lê Đức Thọ, Mỹ Đình, Hà Nội, Vietnam",
  "sections": [
    { "id": "sec-001", "name": "VIP", "basePrice": 2000000, "totalSeats": 5000 },
    { "id": "sec-002", "name": "Khu A", "basePrice": 1000000, "totalSeats": 15000 }
  ]
}
```

---

## 10. State Machine: Event Status

```
                    ┌─────────┐
   Admin tạo event  │         │
   ────────────────►│  DRAFT  │
                    │         │
                    └────┬────┘
                         │ Admin gọi PUT /events/{id}
                         │ { status: "PUBLISHED" }
                         │ event.publish() kiểm tra:
                         │   - phải đang là DRAFT
                         │   - nếu không → 409 Conflict
                         ▼
                    ┌───────────┐
                    │           │◄──────────────────────────────────┐
                    │ PUBLISHED │   User có thể tìm thấy qua search │
                    │           │   và đặt vé qua booking-service   │
                    └─────┬─────┘                                   │
                          │                                          │
          ┌───────────────┴──────────────────┐                      │
          │ Admin cancel event               │ Hết hạn tự nhiên     │
          │ event.cancel()                   │ (future: COMPLETED)  │
          ▼                                  ▼                      │
    ┌───────────┐                      ┌───────────┐               │
    │ CANCELLED │                      │ COMPLETED │               │
    │           │                      │           │               │
    └───────────┘                      └───────────┘               │
    Không thể đặt vé                  Sự kiện kết thúc             │
    Không thể restore                                               │
```

**Quy tắc:**
- `DRAFT` → `PUBLISHED`: được (event.publish())
- `PUBLISHED` → `CANCELLED`: được (event.cancel())
- `CANCELLED` → bất kỳ: KHÔNG được (BusinessException)
- `PUBLISHED` → `DRAFT`: KHÔNG được
- Khi `isBookable()` = true: status=PUBLISHED AND startTime > now AND availableSeats > 0

---

## 11. State Machine: Seat Status

```
                         ┌───────────┐
  Khi event được tạo     │           │
  ─────────────────────► │ AVAILABLE │◄────────────────────────────────┐
                         │           │                                  │
                         └─────┬─────┘                                  │
                               │                                        │
                               │ booking-service: user chọn ghế        │
                               │ → booking-service giữ Redisson lock   │
                               │ → publish "AVAILABLE → LOCKED"        │
                               ▼                                        │
                         ┌──────────┐                                   │
                         │          │─── Payment timeout ──────────────►│
                         │  LOCKED  │─── Payment failed ───────────────►│
                         │          │─── Booking cancel (trước payment)►│
                         └────┬─────┘                                   │
                              │                                         │
                              │ Payment thành công                      │
                              │ → booking-service publish               │
                              │   "LOCKED → BOOKED"                     │
                              ▼                                         │
                         ┌──────────┐                                   │
                         │          │─── User cancel booking ──────────►│
                         │  BOOKED  │    (sau payment thành công)       │
                         │          │                                   │
                         └──────────┘
```

**Màu hiển thị trên UI:**
- `AVAILABLE` → 🟢 Xanh (có thể chọn)
- `LOCKED` → 🟡 Vàng (đang được giữ bởi người khác, tạm thời không chọn được)
- `BOOKED` → 🔴 Đỏ (đã bán)

---

## 12. Chiến lược Cache Redis

Hệ thống có 2 loại cache riêng biệt:

### Cache 1: Event Info Cache

| Thuộc tính | Giá trị |
|---|---|
| **Key pattern** | `event:info:{eventId}` |
| **Ví dụ key** | `event:info:evt-uuid-001` |
| **TTL** | **10 phút** |
| **Lưu gì** | `EventResponse` object (JSON) |
| **Cache được set khi** | `GET /api/v1/events/{id}` cache miss |
| **Cache bị xóa khi** | Admin gọi `PUT /api/v1/events/{id}` |
| **Class xử lý** | `EventCacheService.java` |
| **Mục đích** | Giảm DB query khi nhiều user xem cùng event |

### Cache 2: Seat Map Cache

| Thuộc tính | Giá trị |
|---|---|
| **Key pattern** | `event:seatmap:{eventId}` |
| **Ví dụ key** | `event:seatmap:evt-uuid-001` |
| **TTL** | **5 giây** |
| **Lưu gì** | `SeatMapResponse` object (JSON, bao gồm tất cả ghế grouped by section) |
| **Cache được set khi** | `GET /api/v1/seats/event/{eventId}` cache miss |
| **Cache bị xóa ngay khi** | Kafka nhận `SeatStatusChangedEvent` |
| **Class xử lý** | `SeatCacheService.java` |
| **Mục đích** | Giảm 99% DB load khi event hot – không cần query hàng nghìn ghế mỗi request |

### Cache Flow tổng hợp:

```
Request đến               Cache HIT?          Cache MISS
     │                        │                    │
     ▼                        ▼                    ▼
Check Redis ──── YES ──► Trả về ngay         Query PostgreSQL
                               │                    │
                               │                    ▼
                               │              Lưu vào Redis
                               │              (với TTL)
                               │                    │
                               └──────── OK ────────┘
                                         │
                                         ▼
                                   Trả về Client

Khi data thay đổi (Update/Kafka):
     │
     ▼
Redis DEL key ──► Lần query tiếp theo sẽ warm cache từ DB mới
```

### Cấu hình Redis (CacheConfig.java):
- **Key serializer:** `StringRedisSerializer` → key đọc được trong Redis Insight: `event:info:abc123`
- **Value serializer:** `GenericJackson2JsonRedisSerializer` → lưu dạng JSON, dễ debug
- **Connection pool:** max-active=16, max-idle=8 (Lettuce)

---

## 13. Cấu hình hệ thống và Infrastructure

### Startup Flow (EventServiceApplication.java)

```
JVM Start
    │
    ▼
@SpringBootApplication → scan tất cả @Component, @Service, @Repository, @Controller
@EnableDiscoveryClient → đăng ký với Eureka Server (service registry)
@EnableCaching         → bật Spring Cache (dùng Redis)
    │
    ▼
Liquibase khởi động:
  db.changelog-master.xml
    ├── V001__create_venues_table.xml       → CREATE TABLE venues
    ├── V002__create_events_table.xml       → CREATE TABLE events (với FK → venues)
    ├── V003__create_seat_sections_table.xml → CREATE TABLE seat_sections (với FK → venues)
    └── V004__create_seats_table.xml        → CREATE TABLE seats (với FK → events, seat_sections)
  Kiểm tra migration history → chỉ chạy các migration chưa chạy
    │
    ▼
Kafka Consumer khởi động (KafkaConfig):
  - Tạo ConsumerFactory: group-id = "event-service-group"
  - Manual Acknowledgment (MANUAL_IMMEDIATE)
  - Concurrency = 3 threads
  - ErrorHandler với DLQ: 3 retries × 1 giây → seat.status.changed.DLT
    │
    ▼
Redis connection kiểm tra (CacheConfig):
  - RedisTemplate với String key + JSON value
    │
    ▼
Eureka registration:
  - Đăng ký service "event-service" tại http://service-registry:8761/eureka/
    │
    ▼
Swagger UI sẵn sàng: http://localhost:8082/swagger-ui.html
    │
    ▼
Service READY trên port 8082
```

### Kafka Configuration (KafkaConfig.java)

```
Consumer:
  bootstrap-servers: localhost:29092 (hoặc biến môi trường KAFKA_BOOTSTRAP_SERVERS)
  group-id: event-service-group
  auto-offset-reset: earliest (khi group mới, đọc từ đầu)
  enable-auto-commit: FALSE  → Manual ACK
  max-poll-records: 50       → xử lý tối đa 50 messages/lần poll
  Deserializer: JsonDeserializer<SeatStatusChangedEvent>
  trusted packages: "com.ticketmaster.common.event"

Listener Container:
  ack-mode: MANUAL_IMMEDIATE (commit ngay sau ack())
  concurrency: 3 threads     → cân bằng với 6 partitions của topic
  error handler: DefaultErrorHandler
    backoff: FixedBackOff(1000ms, 3 retries)
    recoverer: DeadLetterPublishingRecoverer → gửi vào *.DLT topic

Producer (chỉ dùng để gửi DLQ):
  acks: "1"    → leader ACK là đủ
  retries: 3
```

### Monitoring (Actuator + Prometheus)

```
GET /actuator/health     → trạng thái service (DB connection, Redis, Kafka)
GET /actuator/metrics    → metrics JVM, HTTP, DB pool
GET /actuator/prometheus → scrape metrics cho Prometheus/Grafana
```

---

## 14. Sơ đồ Database Schema

```
┌──────────────────────────────────────────────────────────────────────┐
│  venues                                                              │
│  ─────────────────────────────────────────────────────────────────  │
│  id          VARCHAR(36) PK                                          │
│  name        VARCHAR(255) NOT NULL                                   │
│  address     VARCHAR(500) NOT NULL                                   │
│  city        VARCHAR(100) NOT NULL   ← INDEX: idx_venues_city        │
│  country     VARCHAR(100) NOT NULL                                   │
│  capacity    INT NOT NULL                                            │
│  created_at  TIMESTAMPTZ NOT NULL                                    │
│  updated_at  TIMESTAMPTZ NOT NULL                                    │
└──────────────────────────────────────────────────────────────────────┘
         │ 1
         │ N
┌──────────────────────────────────────────────────────────────────────┐
│  seat_sections                                                       │
│  ─────────────────────────────────────────────────────────────────  │
│  id          VARCHAR(36) PK                                          │
│  venue_id    VARCHAR(36) NOT NULL FK → venues.id                     │
│  name        VARCHAR(100) NOT NULL  (vd: "VIP", "Khu A")            │
│  description VARCHAR(500)                                            │
│  base_price  DECIMAL(10,2) NOT NULL                                  │
│  total_seats INT NOT NULL                                            │
└──────────────────────────────────────────────────────────────────────┘
         │ 1
         │ N           ┌────────────────────────────────────────────────────────────┐
         │             │  events                                                    │
         │             │  ──────────────────────────────────────────────────────── │
         │             │  id              VARCHAR(36) PK                            │
         │             │  name            VARCHAR(255) NOT NULL                     │
         │             │  description     TEXT                                      │
         │             │  venue_id        VARCHAR(36) NOT NULL FK → venues.id       │
         │             │  venue_name      VARCHAR(255) NOT NULL  ← denormalized     │
         │             │  start_time      TIMESTAMPTZ NOT NULL                      │
         │             │  end_time        TIMESTAMPTZ                               │
         │             │  status          VARCHAR(20) DEFAULT 'DRAFT'               │
         │             │  image_url       VARCHAR(500)                              │
         │             │  category        VARCHAR(50)                               │
         │             │  total_seats     INT NOT NULL                              │
         │             │  available_seats INT NOT NULL  ← cached counter từ Kafka  │
         │             │  created_at      TIMESTAMPTZ NOT NULL                      │
         │             │  updated_at      TIMESTAMPTZ NOT NULL                      │
         │             │                                                            │
         │             │  INDEX: idx_events_status_start    (status, start_time)    │
         │             │  INDEX: idx_events_venue_id        (venue_id)              │
         │             │  INDEX: idx_events_category_status (category, status)      │
         │             └────────────────────────────────────────────────────────────┘
         │                       │ 1
         │ N                     │ N
         └───────────────────────┘
                     │
         ┌──────────────────────────────────────────────────────────────────────┐
         │  seats                                                               │
         │  ─────────────────────────────────────────────────────────────────  │
         │  id          VARCHAR(36) PK                                          │
         │  event_id    VARCHAR(36) NOT NULL FK → events.id                     │
         │  section_id  VARCHAR(36) NOT NULL FK → seat_sections.id              │
         │  row         VARCHAR(5) NOT NULL   (vd: "A", "B")                   │
         │  number      VARCHAR(5) NOT NULL   (vd: "1", "15")                  │
         │  price       DECIMAL(10,2) NOT NULL                                  │
         │  status      VARCHAR(20) NOT NULL  (AVAILABLE|LOCKED|BOOKED)        │
         │                                                                      │
         │  INDEX: idx_seats_event_id      (event_id)                          │
         │  INDEX: idx_seats_event_status  (event_id, status)  ← CRITICAL      │
         │  INDEX: idx_seats_event_section (event_id, section_id)               │
         └──────────────────────────────────────────────────────────────────────┘
```

---

## 15. Tóm tắt quan hệ giữa các file

### Bảng tra cứu nhanh: File nào gọi File nào

| File (Caller) | Gọi (Callee) | Để làm gì |
|---|---|---|
| `EventController` | `CreateEventHandler` | Tạo event mới |
| `EventController` | `UpdateEventHandler` | Cập nhật event |
| `EventController` | `SearchEventsHandler` | Tìm kiếm events |
| `EventController` | `EventCacheService` | Đọc/xóa cache event info |
| `EventController` | `EventRepository` | Đọc event theo ID (getById) |
| `EventController` | `EventMapper` | Convert domain → DTO |
| `SeatController` | `GetSeatMapHandler` | Lấy seat map |
| `VenueController` | `VenueRepository` | Đọc venue |
| `VenueController` | `VenueMapper` | Convert domain → DTO |
| `CreateEventHandler` | `EventDomainService` | Validate venue tồn tại |
| `CreateEventHandler` | `VenueRepository` | Lấy thông tin venue |
| `CreateEventHandler` | `EventRepository` | Lưu event mới |
| `CreateEventHandler` | `EventMapper` | Convert domain → DTO |
| `UpdateEventHandler` | `EventRepository` | Đọc + lưu event |
| `UpdateEventHandler` | `EventCacheService` | Evict cache sau khi update |
| `UpdateEventHandler` | `EventMapper` | Convert domain → DTO |
| `SearchEventsHandler` | `EventRepository` | Tìm kiếm với filter |
| `SearchEventsHandler` | `EventMapper` | Convert domain → DTO |
| `GetSeatMapHandler` | `EventRepository` | Validate event tồn tại |
| `GetSeatMapHandler` | `SeatRepository` | Lấy tất cả ghế của event |
| `GetSeatMapHandler` | `SeatCacheService` | Đọc/ghi cache seat map |
| `GetSeatMapHandler` | `SeatMapper` | Convert Seat → SeatResponse |
| `SeatStatusEventConsumer` | `EventDomainService` | Cập nhật seat status trong DB |
| `SeatStatusEventConsumer` | `SeatCacheService` | Evict cache seat map |
| `EventDomainService` | `VenueRepository` | Validate venue tồn tại |
| `EventDomainService` | `EventRepository` | Đọc/lưu event |
| `EventDomainService` | `SeatRepository` | Cập nhật seat, đếm available |
| `EventJpaRepository` | `SpringDataEventRepository` | Thực thi SQL qua JPA |
| `EventJpaRepository` | `EventMapper` | Convert JPA Entity ↔ Domain |
| `SeatJpaRepository` | `SpringDataSeatRepository` | Thực thi SQL qua JPA |
| `SeatJpaRepository` | `SeatMapper` | Convert JPA Entity ↔ Domain |
| `VenueJpaRepository` | `SpringDataVenueRepository` | Thực thi SQL qua JPA |
| `VenueJpaRepository` | `VenueMapper` | Convert JPA Entity ↔ Domain |
| `EventCacheService` | `RedisTemplate` | GET/SET/DEL Redis key |
| `SeatCacheService` | `RedisTemplate` | GET/SET/DEL Redis key |

### Nguyên tắc phân tầng quan trọng cần nhớ

```
✅ ĐƯỢC PHÉP:
  interfaces  → application   (Controller gọi Handler)
  application → domain        (Handler gọi DomainService, Repository interface)
  application → infrastructure (Handler gọi CacheService)
  infrastructure → domain     (JpaRepository implement domain Repository interface)

❌ KHÔNG ĐƯỢC PHÉP:
  domain → application        (domain không biết đến handler)
  domain → infrastructure     (domain không import Spring/JPA)
  domain → interfaces         (domain không biết đến DTO)
  interfaces → infrastructure trực tiếp (nên đi qua application layer)
```

> **Ngoại lệ trong dự án này:**
> `EventController` và `VenueController` có một số chỗ gọi trực tiếp vào `EventRepository`, `EventCacheService`, `EventMapper` – đây là shortcut nhỏ cho các operation đơn giản (read-only) để tránh tạo Handler chỉ có vài dòng code.

---

*Tài liệu này phản ánh trạng thái code tại thời điểm: February 2026*
*Service port: 8082 | Database: event_db (PostgreSQL) | Cache: Redis prefix "event:"*


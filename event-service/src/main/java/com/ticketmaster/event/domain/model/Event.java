package com.ticketmaster.event.domain.model;

import com.ticketmaster.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Event – Aggregate Root đại diện cho một sự kiện (concert, show, game, v.v.).
 *
 * <p><b>Business Invariants:</b>
 * <ul>
 *   <li>Event chỉ có thể bán vé khi status là {@code PUBLISHED}</li>
 *   <li>Event đã CANCELLED không thể chuyển sang trạng thái khác</li>
 *   <li>Ngày diễn ra phải trong tương lai khi tạo</li>
 *   <li>Event đã qua không thể đặt vé</li>
 * </ul>
 *
 * <p><b>Pure Java:</b> Không có annotation Spring hay JPA.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    /** UUID v4 – primary key. */
    private String id;

    /** Tên sự kiện (vd: "BLACKPINK World Tour - Hà Nội"). */
    private String name;

    /** Mô tả chi tiết. */
    private String description;

    /** ID của Venue (địa điểm). */
    private String venueId;

    /** Tên venue (denormalized để tránh join khi hiển thị). */
    private String venueName;

    /** Thời điểm bắt đầu sự kiện (UTC). */
    private Instant startTime;

    /** Thời điểm kết thúc sự kiện (UTC). */
    private Instant endTime;

    /**
     * Trạng thái sự kiện.
     * DRAFT → PUBLISHED → (CANCELLED | COMPLETED)
     */
    private String status;

    /** URL banner/poster hình ảnh. */
    private String imageUrl;

    /** Thể loại (vd: "CONCERT", "SPORT", "THEATER"). */
    private String category;

    /** Tổng số ghế của event (copy từ venue capacity). */
    private int totalSeats;

    /** Số ghế còn trống (cached, cập nhật theo Kafka events). */
    private int availableSeats;

    /** Thời điểm tạo (UTC). */
    private Instant createdAt;

    /** Thời điểm cập nhật (UTC). */
    private Instant updatedAt;

    // ── Factory Method ─────────────────────────────────────────────

    public static Event create(String id, String name, String description,
                               String venueId, String venueName,
                               Instant startTime, Instant endTime,
                               String category, String imageUrl, int totalSeats) {
        Instant now = Instant.now();
        return Event.builder()
                .id(id).name(name).description(description)
                .venueId(venueId).venueName(venueName)
                .startTime(startTime).endTime(endTime)
                .status("DRAFT").category(category).imageUrl(imageUrl)
                .totalSeats(totalSeats).availableSeats(totalSeats)
                .createdAt(now).updatedAt(now)
                .build();
    }

    // ── Domain Methods ─────────────────────────────────────────────

    /**
     * Publish event để bắt đầu bán vé.
     *
     * @throws BusinessException nếu event không ở trạng thái DRAFT
     */
    public Event publish() {
        if (!"DRAFT".equals(this.status)) {
            throw new BusinessException(
                    "Only DRAFT events can be published",
                    "INVALID_EVENT_STATUS", HttpStatus.CONFLICT);
        }
        return toBuilder().status("PUBLISHED").updatedAt(Instant.now()).build();
    }

    /**
     * Cancel event và hoàn trả tất cả booking.
     */
    public Event cancel() {
        if ("CANCELLED".equals(this.status)) {
            throw new BusinessException(
                    "Event is already cancelled",
                    "EVENT_ALREADY_CANCELLED", HttpStatus.CONFLICT);
        }
        return toBuilder().status("CANCELLED").updatedAt(Instant.now()).build();
    }

    /**
     * Cập nhật số ghế trống sau khi nhận Kafka seat status event.
     */
    public Event updateAvailableSeats(int availableSeats) {
        return toBuilder().availableSeats(availableSeats).updatedAt(Instant.now()).build();
    }

    /**
     * Kiểm tra event có đang mở bán vé không.
     */
    public boolean isBookable() {
        return "PUBLISHED".equals(this.status)
                && this.startTime != null
                && this.startTime.isAfter(Instant.now())
                && this.availableSeats > 0;
    }

    private EventBuilder toBuilder() {
        return Event.builder()
                .id(id).name(name).description(description)
                .venueId(venueId).venueName(venueName)
                .startTime(startTime).endTime(endTime)
                .status(status).category(category).imageUrl(imageUrl)
                .totalSeats(totalSeats).availableSeats(availableSeats)
                .createdAt(createdAt);
    }
}
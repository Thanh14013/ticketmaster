package com.ticketmaster.booking.domain.model;

import com.ticketmaster.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Booking – Aggregate Root của bounded context Booking (CORE).
 *
 * <p>Là trung tâm của toàn bộ luồng đặt vé:
 * <ol>
 *   <li>User chọn ghế → {@code Booking.create()} với status=PENDING_PAYMENT</li>
 *   <li>Redisson lock từng seatId (TTL 2 phút) trong {@link com.ticketmaster.booking.infrastructure.lock.RedissonSeatLockService}</li>
 *   <li>Kafka publish {@code booking.created} → payment-service bắt đầu xử lý</li>
 *   <li>Quartz schedule job release sau 2 phút nếu chưa thanh toán</li>
 *   <li>Nhận {@code payment.processed} → {@code booking.confirm()}</li>
 *   <li>Kafka publish {@code seat.status.changed} (LOCKED→BOOKED)</li>
 * </ol>
 *
 * <p><b>Business Invariants:</b>
 * <ul>
 *   <li>Tối đa {@code MAX_SEATS_PER_BOOKING} ghế một lần (default 8)</li>
 *   <li>Không thể CONFIRM booking đã CANCELLED hoặc EXPIRED</li>
 *   <li>Không thể CANCEL booking đã CONFIRMED mà không có refund flow</li>
 *   <li>totalAmount = sum của tất cả BookingItem.price</li>
 * </ul>
 *
 * <p><b>Pure Java:</b> Không có annotation Spring hay JPA.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    public static final int MAX_SEATS_PER_BOOKING = 8;

    /** UUID v4 – primary key, sinh bởi {@link com.ticketmaster.common.util.IdGenerator}. */
    private String id;

    /** ID của user thực hiện booking. */
    private String userId;

    /** Email user (snapshot để gửi notification không cần join). */
    private String userEmail;

    /** ID của Event (concert/show). */
    private String eventId;

    /** Tên event (snapshot để hiển thị). */
    private String eventName;

    /** Danh sách ghế được đặt trong booking này. */
    private List<BookingItem> items;

    /** Tổng tiền = sum(items.price). */
    private BigDecimal totalAmount;

    /** Trạng thái booking hiện tại. */
    private BookingStatus status;

    /** Transaction ID từ payment-service (null khi chưa thanh toán). */
    private String transactionId;

    /** Lý do hủy (null nếu không bị cancel). */
    private String cancellationReason;

    /** Thời điểm booking hết hạn (createdAt + 2 phút – dùng cho Quartz job). */
    private Instant expiresAt;

    /** Thời điểm tạo (UTC). */
    private Instant createdAt;

    /** Thời điểm cập nhật (UTC). */
    private Instant updatedAt;

    // ── Factory Method ─────────────────────────────────────────────

    /**
     * Tạo Booking mới từ danh sách ghế đã được lock bởi Redisson.
     *
     * @param id          UUID đã sinh sẵn
     * @param userId      ID user
     * @param userEmail   email user (snapshot)
     * @param eventId     ID event
     * @param eventName   tên event (snapshot)
     * @param items       danh sách BookingItem (đã validate)
     * @param lockTtlMinutes thời gian lock ghế (phút)
     * @return Booking mới với status=PENDING_PAYMENT
     */
    public static Booking create(String id, String userId, String userEmail,
                                  String eventId, String eventName,
                                  List<BookingItem> items, int lockTtlMinutes) {
        BigDecimal total = items.stream()
                .map(BookingItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant now = Instant.now();
        return Booking.builder()
                .id(id)
                .userId(userId)
                .userEmail(userEmail)
                .eventId(eventId)
                .eventName(eventName)
                .items(items)
                .totalAmount(total)
                .status(BookingStatus.PENDING_PAYMENT)
                .expiresAt(now.plusSeconds(lockTtlMinutes * 60L))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ── Domain Methods (State Transitions) ─────────────────────────

    /**
     * Xác nhận booking sau khi thanh toán thành công.
     * Chỉ được phép khi status là PENDING_PAYMENT.
     *
     * @param transactionId ID transaction từ payment-service
     * @return Booking mới với status=CONFIRMED
     * @throws BusinessException nếu booking không ở trạng thái PENDING_PAYMENT
     */
    public Booking confirm(String transactionId) {
        if (this.status != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    "Cannot confirm booking with status: " + this.status,
                    "INVALID_BOOKING_STATUS", HttpStatus.CONFLICT);
        }
        return toBuilder()
                .status(BookingStatus.CONFIRMED)
                .transactionId(transactionId)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Huỷ booking theo yêu cầu user hoặc hệ thống.
     * Không được cancel booking đã CONFIRMED (phải có refund flow riêng).
     *
     * @param reason lý do huỷ
     * @return Booking mới với status=CANCELLED
     * @throws BusinessException nếu booking đã CONFIRMED
     */
    public Booking cancel(String reason) {
        if (this.status == BookingStatus.CONFIRMED) {
            throw new BusinessException(
                    "Cannot cancel confirmed booking directly. Use refund flow.",
                    "CANNOT_CANCEL_CONFIRMED", HttpStatus.CONFLICT);
        }
        return toBuilder()
                .status(BookingStatus.CANCELLED)
                .cancellationReason(reason)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Đánh dấu booking hết hạn (do Quartz scheduler sau 2 phút).
     * Chỉ áp dụng cho booking đang PENDING_PAYMENT.
     *
     * @return Booking mới với status=EXPIRED
     */
    public Booking expire() {
        if (this.status != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    "Only PENDING_PAYMENT bookings can expire",
                    "INVALID_BOOKING_STATUS", HttpStatus.CONFLICT);
        }
        return toBuilder()
                .status(BookingStatus.EXPIRED)
                .cancellationReason("Payment timeout – seats automatically released")
                .updatedAt(Instant.now())
                .build();
    }

    // ── Query Methods ──────────────────────────────────────────────

    public boolean isPendingPayment() {
        return BookingStatus.PENDING_PAYMENT.equals(this.status);
    }

    public boolean isConfirmed() {
        return BookingStatus.CONFIRMED.equals(this.status);
    }

    public boolean isActive() {
        return this.status == BookingStatus.PENDING_PAYMENT
                || this.status == BookingStatus.CONFIRMED;
    }

    public boolean isExpired() {
        return BookingStatus.EXPIRED.equals(this.status)
                || (this.expiresAt != null && Instant.now().isAfter(this.expiresAt)
                    && this.status == BookingStatus.PENDING_PAYMENT);
    }

    /** Lấy danh sách seatId từ các BookingItem. */
    public List<String> getSeatIds() {
        return items.stream().map(BookingItem::getSeatId).toList();
    }

    // ── Builder Helper ─────────────────────────────────────────────

    private BookingBuilder toBuilder() {
        return Booking.builder()
                .id(id).userId(userId).userEmail(userEmail)
                .eventId(eventId).eventName(eventName)
                .items(items).totalAmount(totalAmount)
                .status(status).transactionId(transactionId)
                .cancellationReason(cancellationReason)
                .expiresAt(expiresAt).createdAt(createdAt);
    }
}
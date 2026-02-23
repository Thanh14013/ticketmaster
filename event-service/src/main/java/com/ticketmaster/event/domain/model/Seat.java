package com.ticketmaster.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một ghế ngồi cụ thể trong một Event.
 *
 * <p>Một Seat luôn thuộc về một {@link SeatSection} và một Event cụ thể.
 * Trạng thái ghế ({@link SeatStatus}) thay đổi theo booking lifecycle.
 *
 * <p><b>Không phải Aggregate Root</b> – được truy cập qua SeatRepository
 * để đọc/cập nhật trạng thái khi nhận Kafka event từ booking-service.
 *
 * <p>Thông tin ghế trên seat map: row + number + section name.
 * Vd: "Hàng A, Số 5, Khu VIP"
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    /** UUID v4 – primary key. */
    private String id;

    /** ID của Event mà ghế này thuộc về. */
    private String eventId;

    /** ID của SeatSection (khu vực) chứa ghế này. */
    private String sectionId;

    /** Hàng (vd: "A", "B", "C"). */
    private String row;

    /** Số ghế trong hàng (vd: "1", "2", "15"). */
    private String number;

    /** Giá ghế này trong event (có thể khác basePrice của section). */
    private BigDecimal price;

    /** Trạng thái hiện tại của ghế. */
    private SeatStatus status;

    // ── Domain Methods ────────────────────────────────────────────

    /**
     * Chuyển trạng thái ghế sang {@link SeatStatus#LOCKED}.
     * Chỉ được phép khi ghế đang AVAILABLE.
     *
     * @return Seat mới với status=LOCKED
     * @throws IllegalStateException nếu ghế không ở trạng thái AVAILABLE
     */
    public Seat lock() {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Cannot lock seat " + this.id + " with status " + this.status);
        }
        return Seat.builder()
                .id(this.id).eventId(this.eventId).sectionId(this.sectionId)
                .row(this.row).number(this.number).price(this.price)
                .status(SeatStatus.LOCKED)
                .build();
    }

    /**
     * Chuyển trạng thái ghế sang {@link SeatStatus#BOOKED}.
     *
     * @return Seat mới với status=BOOKED
     */
    public Seat book() {
        return Seat.builder()
                .id(this.id).eventId(this.eventId).sectionId(this.sectionId)
                .row(this.row).number(this.number).price(this.price)
                .status(SeatStatus.BOOKED)
                .build();
    }

    /**
     * Trả ghế về trạng thái {@link SeatStatus#AVAILABLE}.
     * Dùng khi booking timeout, payment fail, hoặc booking bị cancel.
     *
     * @return Seat mới với status=AVAILABLE
     */
    public Seat release() {
        return Seat.builder()
                .id(this.id).eventId(this.eventId).sectionId(this.sectionId)
                .row(this.row).number(this.number).price(this.price)
                .status(SeatStatus.AVAILABLE)
                .build();
    }

    /**
     * Cập nhật trạng thái ghế theo string (dùng khi nhận từ Kafka event).
     *
     * @param newStatus string status từ {@code SeatStatusChangedEvent}
     * @return Seat mới với status đã cập nhật
     */
    public Seat withStatus(String newStatus) {
        return Seat.builder()
                .id(this.id).eventId(this.eventId).sectionId(this.sectionId)
                .row(this.row).number(this.number).price(this.price)
                .status(SeatStatus.valueOf(newStatus))
                .build();
    }

    /**
     * @return true nếu ghế có thể đặt
     */
    public boolean isAvailable() {
        return SeatStatus.AVAILABLE.equals(this.status);
    }

    /**
     * @return label hiển thị trên seat map (vd: "A-5")
     */
    public String getDisplayLabel() {
        return this.row + "-" + this.number;
    }
}
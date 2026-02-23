package com.ticketmaster.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Venue – Aggregate Root đại diện cho địa điểm tổ chức sự kiện.
 *
 * <p>Ví dụ: "Sân vận động Mỹ Đình", "Nhà hát Lớn Hà Nội".
 * Một Venue có nhiều {@link SeatSection} (khu vực ghế).
 * Nhiều {@link Event} có thể diễn ra tại cùng một Venue.
 *
 * <p><b>Pure Java:</b> Không có annotation Spring hay JPA.
 * Mapping sang DB qua {@link com.ticketmaster.event.infrastructure.persistence.mapper.VenueMapper}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Venue {

    /** UUID v4 – primary key. */
    private String id;

    /** Tên địa điểm (vd: "Sân vận động Mỹ Đình"). */
    private String name;

    /** Địa chỉ đầy đủ. */
    private String address;

    /** Thành phố (vd: "Hà Nội", "TP. Hồ Chí Minh"). */
    private String city;

    /** Quốc gia (vd: "Vietnam"). */
    private String country;

    /** Sức chứa tối đa (tổng tất cả ghế trong venue). */
    private int capacity;

    /** Danh sách khu vực ghế trong venue này. */
    private List<SeatSection> sections;

    /** Thời điểm tạo (UTC). */
    private Instant createdAt;

    /** Thời điểm cập nhật (UTC). */
    private Instant updatedAt;

    // ── Factory Method ────────────────────────────────────────────

    public static Venue create(String id, String name, String address,
                               String city, String country, int capacity) {
        Instant now = Instant.now();
        return Venue.builder()
                .id(id).name(name).address(address)
                .city(city).country(country).capacity(capacity)
                .createdAt(now).updatedAt(now)
                .build();
    }

    // ── Domain Methods ────────────────────────────────────────────

    public Venue update(String name, String address, String city, String country, int capacity) {
        return Venue.builder()
                .id(this.id).name(name).address(address)
                .city(city).country(country).capacity(capacity)
                .sections(this.sections)
                .createdAt(this.createdAt).updatedAt(Instant.now())
                .build();
    }

    public String getDisplayAddress() {
        return address + ", " + city + ", " + country;
    }
}
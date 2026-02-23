package com.ticketmaster.event.infrastructure.cache;

import com.ticketmaster.event.interfaces.dto.SeatMapResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis Cache Service cho Seat Map – tính năng có traffic cao nhất trong hệ thống.
 *
 * <p><b>Tại sao cache Seat Map riêng?</b>
 * Khi event hot (BLACKPINK, BTS), hàng nghìn user đồng thời xem seat map.
 * Mỗi request render seat map cần đọc hàng trăm rows từ DB.
 * Cache với TTL ngắn (5 giây) giúp giảm DB load 99% mà vẫn đảm bảo data gần real-time.
 *
 * <p><b>Cache key pattern:</b> {@code event:seatmap:{eventId}}
 * <p><b>TTL:</b> 5 giây – ngắn để đảm bảo tính chính xác khi nhiều user booking đồng thời.
 *
 * <p>Cache bị evict ngay lập tức khi nhận {@code SeatStatusChangedEvent} từ Kafka.
 * Lần query tiếp theo sẽ warm cache từ DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheService {

    private static final String   KEY_PREFIX = "event:seatmap:";
    /**
     * TTL ngắn 5 giây: cân bằng giữa performance và consistency.
     * Trong 5 giây, có thể tối đa ~5 users nhìn thấy trạng thái ghế cũ – chấp nhận được.
     * Booking-service có Redisson distributed lock để tránh double-booking thực sự.
     */
    private static final Duration TTL        = Duration.ofSeconds(5);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lưu SeatMapResponse vào Redis.
     */
    public void cacheSeatMap(String eventId, SeatMapResponse response) {
        String key = KEY_PREFIX + eventId;
        try {
            redisTemplate.opsForValue().set(key, response, TTL);
            log.debug("[CACHE] SeatMap cached: key={} seats={}",
                    key, response.getTotalSeats());
        } catch (Exception e) {
            log.warn("[CACHE] Failed to cache seat map for eventId={}: {}", eventId, e.getMessage());
        }
    }

    /**
     * Lấy SeatMapResponse từ Redis cache.
     *
     * @return SeatMapResponse nếu cache hit, null nếu miss
     */
    public SeatMapResponse getSeatMap(String eventId) {
        String key = KEY_PREFIX + eventId;
        try {
            return (SeatMapResponse) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[CACHE] Failed to get seat map from cache {}: {}", eventId, e.getMessage());
            return null;
        }
    }

    /**
     * Xóa seat map cache ngay lập tức khi có Kafka seat status event.
     * Không chờ TTL để đảm bảo tính chính xác.
     */
    public void evictSeatMap(String eventId) {
        String key = KEY_PREFIX + eventId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("[CACHE] SeatMap cache evicted: key={} deleted={}", key, deleted);
        } catch (Exception e) {
            log.warn("[CACHE] Failed to evict seat map cache {}: {}", eventId, e.getMessage());
        }
    }
}
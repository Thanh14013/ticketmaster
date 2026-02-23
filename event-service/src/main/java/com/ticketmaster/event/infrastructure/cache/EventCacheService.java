package com.ticketmaster.event.infrastructure.cache;

import com.ticketmaster.event.interfaces.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis Cache Service cho thông tin Event.
 *
 * <p><b>Cache key pattern:</b> {@code event:info:{eventId}}
 * <p><b>TTL:</b> 10 phút – event info ít thay đổi, đủ để giảm DB load khi traffic cao.
 *
 * <p>Cache bị evict khi:
 * <ul>
 *   <li>Admin cập nhật event (UpdateEventHandler)</li>
 *   <li>availableSeats thay đổi sau khi nhận Kafka event</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCacheService {

    private static final String KEY_PREFIX = "event:info:";
    private static final Duration TTL      = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lưu EventResponse vào Redis cache.
     */
    public void cacheEvent(String eventId, EventResponse response) {
        String key = KEY_PREFIX + eventId;
        try {
            redisTemplate.opsForValue().set(key, response, TTL);
            log.debug("[CACHE] Event cached: key={}", key);
        } catch (Exception e) {
            log.warn("[CACHE] Failed to cache event {}: {}", eventId, e.getMessage());
            // Cache failure không nên block business logic
        }
    }

    /**
     * Lấy EventResponse từ Redis cache.
     *
     * @return EventResponse nếu cache hit, null nếu miss
     */
    public EventResponse getEvent(String eventId) {
        String key = KEY_PREFIX + eventId;
        try {
            return (EventResponse) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[CACHE] Failed to get event from cache {}: {}", eventId, e.getMessage());
            return null;
        }
    }

    /**
     * Xóa cache của event (khi event được update).
     */
    public void evictEventCache(String eventId) {
        String key = KEY_PREFIX + eventId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("[CACHE] Event cache evicted: key={} deleted={}", key, deleted);
        } catch (Exception e) {
            log.warn("[CACHE] Failed to evict event cache {}: {}", eventId, e.getMessage());
        }
    }
}
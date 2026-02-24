package com.ticketmaster.booking.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed Lock Service dùng Redisson để lock ghế khi user đặt vé.
 *
 * <p>Đây là component CRITICAL nhất trong hệ thống – đảm bảo rằng
 * cùng một ghế không thể bị đặt bởi 2 user đồng thời (overselling prevention).
 *
 * <p><b>Lock key pattern:</b> {@code seat:lock:{seatId}}
 * <br>Ví dụ: {@code seat:lock:abc123-seat-001}
 *
 * <p><b>Cơ chế hoạt động:</b>
 * <ol>
 *   <li>User A gọi {@code tryLock("seat-001")} → Redis SET NX EX 120</li>
 *   <li>User B gọi {@code tryLock("seat-001")} → trả về false (đã bị giữ)</li>
 *   <li>Sau 2 phút (TTL) hoặc khi payment thành công → {@code unlock("seat-001")}</li>
 * </ol>
 *
 * <p><b>tryLock vs lock:</b> Dùng {@code tryLock} (non-blocking) thay vì {@code lock}
 * để tránh user phải chờ. Nếu ghế bị giữ → trả lỗi ngay lập tức.
 *
 * <p><b>TTL:</b> Lock tự expire sau {@code seatLockTtlMinutes} phút ngay cả khi
 * service crash → tránh ghost locks. Quartz job cũng chạy độc lập để release.
 *
 * <p><b>Redisson vs Jedis:</b> Dùng Redisson vì hỗ trợ Lua script atomic operations,
 * watchdog mechanism, và cluster-aware operations out of the box.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonSeatLockService {

    private static final String LOCK_KEY_PREFIX = "seat:lock:";

    private final RedissonClient redissonClient;

    @Value("${booking.seat-lock-ttl-minutes:2}")
    private int seatLockTtlMinutes;

    /**
     * Cố gắng lấy distributed lock cho một ghế.
     *
     * <p><b>Non-blocking:</b> Trả về ngay lập tức (waitTime = 0).
     * Nếu ghế đang bị lock bởi thread/process khác → trả về {@code false}.
     *
     * @param seatId       ID ghế cần lock
     * @param ttlMinutes   thời gian lock tồn tại (phút) – nên bằng booking timeout
     * @return {@code true} nếu lock thành công, {@code false} nếu ghế đang bị giữ
     */
    public boolean tryLock(String seatId, int ttlMinutes) {
        String lockKey = buildLockKey(seatId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // waitTime=0: không chờ, trả về false ngay nếu không lấy được lock
            // leaseTime=ttlMinutes: lock tự expire sau ttlMinutes phút
            boolean acquired = lock.tryLock(0, ttlMinutes, TimeUnit.MINUTES);

            if (acquired) {
                log.debug("[REDISSON] Lock acquired: key={} ttl={}min", lockKey, ttlMinutes);
            } else {
                log.info("[REDISSON] Lock FAILED (seat held by another user): key={}", lockKey);
            }

            return acquired;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[REDISSON] Thread interrupted while acquiring lock: key={}", lockKey);
            return false;
        }
    }

    /**
     * Giải phóng distributed lock của một ghế.
     *
     * <p><b>Safety:</b> Redisson chỉ cho phép unlock lock do chính thread/instance
     * đang giữ. Nếu lock đã expire (TTL) hoặc chưa bao giờ được lock → không có tác dụng.
     *
     * @param seatId ID ghế cần unlock
     */
    public void unlock(String seatId) {
        String lockKey = buildLockKey(seatId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[REDISSON] Lock released: key={}", lockKey);
            } else {
                // Lock đã expire hoặc được hold bởi thread khác (scheduler)
                // Dùng forceUnlock để đảm bảo lock luôn được release
                boolean forceUnlocked = lock.forceUnlock();
                log.debug("[REDISSON] Force unlock: key={} success={}", lockKey, forceUnlocked);
            }
        } catch (Exception e) {
            log.warn("[REDISSON] Error while unlocking key={}: {}", lockKey, e.getMessage());
        }
    }

    /**
     * Kiểm tra ghế có đang bị lock không.
     * Dùng để validate trước khi booking (optional check).
     *
     * @param seatId ID ghế cần kiểm tra
     * @return {@code true} nếu ghế đang bị lock
     */
    public boolean isLocked(String seatId) {
        String lockKey = buildLockKey(seatId);
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * Gia hạn TTL của lock (dùng nếu cần extend thời gian thanh toán).
     *
     * @param seatId         ID ghế cần gia hạn
     * @param extendMinutes  thêm bao nhiêu phút
     */
    public void extendLock(String seatId, int extendMinutes) {
        String lockKey = buildLockKey(seatId);
        RLock lock = redissonClient.getLock(lockKey);

        if (lock.isLocked()) {
            lock.expire(extendMinutes, TimeUnit.MINUTES);
            log.info("[REDISSON] Lock extended: key={} +{}min", lockKey, extendMinutes);
        }
    }

    private String buildLockKey(String seatId) {
        return LOCK_KEY_PREFIX + seatId;
    }
}
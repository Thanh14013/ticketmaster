package com.ticketmaster.booking.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson Client configuration cho Distributed Lock.
 *
 * <p>Redisson được chọn thay vì Jedis/Lettuce vì:
 * <ul>
 *   <li>Native distributed lock support (RLock) với watchdog mechanism</li>
 *   <li>Lua script atomic operations (SET NX EX)</li>
 *   <li>Automatic lock expiry khi client crash</li>
 *   <li>Cluster/Sentinel mode support out of the box</li>
 * </ul>
 *
 * <p><b>Single server mode:</b> Cho single Redis instance (development/staging).
 * Production nên dùng {@code useSentinelServers()} hoặc {@code useClusterServers()}.
 *
 * <p><b>Connection pool:</b> Tuned cho booking service với concurrency cao
 * (nhiều users đồng thời chọn ghế trong event hot).
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(16)        // Max connections trong pool
                .setConnectionMinimumIdleSize(4)  // Min idle connections
                .setConnectTimeout(3000)          // Connection timeout (ms)
                .setIdleConnectionTimeout(10000)  // Idle connection cleanup
                .setRetryAttempts(3)              // Retry on command failure
                .setRetryInterval(500)            // Retry interval (ms)
                .setTimeout(3000)                 // Command timeout (ms)
                .setDatabase(0);                  // Redis DB index

        log.info("[REDISSON] Connecting to Redis at {} (pool: 4-16)", address);

        return Redisson.create(config);
    }
}
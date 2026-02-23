package com.ticketmaster.user.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis Cache configuration cho user-service.
 *
 * <p><b>Cache names và TTL:</b>
 * <ul>
 *   <li>{@code users}  – thông tin user profile, TTL 1 giờ</li>
 * </ul>
 *
 * <p>Key serializer: {@link StringRedisSerializer} → key dễ đọc trong Redis Insight.
 * Value serializer: {@link GenericJackson2JsonRedisSerializer} → lưu dạng JSON.
 *
 * <p>Cache key pattern: {@code users::userId} (vd: {@code users::abc123def456})
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration USER_CACHE_TTL = Duration.ofHours(1);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(USER_CACHE_TTL)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();  // Không cache null values

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "users", defaultConfig.entryTtl(USER_CACHE_TTL)
                ))
                .build();
    }
}
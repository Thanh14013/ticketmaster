package com.ticketmaster.event.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis cache configuration cho event-service.
 *
 * <p>Cấu hình {@link RedisTemplate} dùng cho {@link com.ticketmaster.event.infrastructure.cache.EventCacheService}
 * và {@link com.ticketmaster.event.infrastructure.cache.SeatCacheService}.
 *
 * <p>Key: String (human-readable trong Redis Insight)
 * Value: JSON (dễ debug, type-safe với Jackson)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key: String serializer → key dễ đọc: "event:info:abc123"
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value: JSON serializer → lưu dạng JSON object
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
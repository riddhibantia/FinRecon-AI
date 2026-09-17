package com.finrecon.ingestion.messaging;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// P5 Redis dedupe for broker deployments. SETNX-with-TTL per eventId;
// inert unless finrecon.redis.enabled=true (no Redis locally).
@Component
@ConditionalOnProperty(name = "finrecon.redis.enabled", havingValue = "true")
public class RedisDedupeStore implements DedupeStore {

    static final Duration TTL = Duration.ofHours(24);
    static final String PREFIX = "finrecon:dedupe:";

    private final StringRedisTemplate redis;

    public RedisDedupeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryClaim(String key) {
        Boolean claimed = redis.opsForValue()
                .setIfAbsent(PREFIX + key, "1", TTL);
        return Boolean.TRUE.equals(claimed);
    }
}

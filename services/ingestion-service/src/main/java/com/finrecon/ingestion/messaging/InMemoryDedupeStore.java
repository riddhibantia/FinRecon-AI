package com.finrecon.ingestion.messaging;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// P5 default dedupe: process-local, TTL-bounded. Active unless Redis
// dedupe is enabled (property switch, not bean ordering, so the outcome
// is deterministic). NOT shared across instances; broker deployments use
// RedisDedupeStore instead.
@Component
@ConditionalOnProperty(name = "finrecon.redis.enabled", havingValue = "false",
        matchIfMissing = true)
public class InMemoryDedupeStore implements DedupeStore {

    static final Duration TTL = Duration.ofHours(24);

    private final Map<String, Long> claims = new ConcurrentHashMap<>();

    @Override
    public boolean tryClaim(String key) {
        long now = System.currentTimeMillis();
        Long previous = claims.putIfAbsent(key, now + TTL.toMillis());
        if (previous == null) {
            return true;
        }
        if (previous < now) {
            return claims.replace(key, previous, now + TTL.toMillis());
        }
        return false;
    }
}

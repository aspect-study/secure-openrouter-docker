package com.openrouter.gateway.ratelimit;

import com.openrouter.gateway.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limiting using Bucket4j (token bucket algorithm).
 *
 * Each user gets their own Bucket, lazily created on first request.
 * Stored in-memory (ConcurrentHashMap) — sufficient for Phase 2.
 *
 * For Phase 3+: replace with Bucket4j + Redis for distributed rate limiting
 * across multiple app instances.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int requestsPerMinute;

    public RateLimitService(AppProperties appProperties) {
        this.capacity = appProperties.getRateLimit().getCapacity();
        this.requestsPerMinute = appProperties.getRateLimit().getRequestsPerMinute();
    }

    /**
     * Attempts to consume one token from the user's bucket.
     *
     * @param userEmail the authenticated user's email (bucket key)
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryConsume(String userEmail) {
        Bucket bucket = buckets.computeIfAbsent(userEmail, this::newBucket);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for user: {}", userEmail);
        }
        return allowed;
    }

    /**
     * Returns available tokens remaining for the user.
     */
    public long availableTokens(String userEmail) {
        Bucket bucket = buckets.get(userEmail);
        return bucket == null ? capacity : bucket.getAvailableTokens();
    }

    // ── Private ───────────────────────────────────────────────────────────

    private Bucket newBucket(String userEmail) {
        // Refill at requestsPerMinute tokens per minute, max capacity tokens
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}

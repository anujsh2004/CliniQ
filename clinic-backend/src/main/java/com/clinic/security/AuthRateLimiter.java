package com.clinic.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Counts authentication attempts per client, in Redis.
 *
 * <p>Redis rather than memory because the count has to hold across every
 * instance of the application: a limiter that only sees one instance's traffic
 * multiplies the real allowance by the number of instances, which is precisely
 * the situation an attacker benefits from.
 *
 * <p><b>Fails open.</b> If Redis is unreachable the request is allowed through
 * and the failure is logged. The alternative - failing closed - would mean a
 * Redis outage locks every user out of the product, turning a cache problem
 * into a total outage. Rate limiting slows credential stuffing; it is not the
 * thing that keeps accounts safe, and bcrypt password hashing does not stop
 * working when Redis does.
 */
@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:auth:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public AuthRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Records one attempt and reports whether the client has now exceeded its
     * allowance.
     *
     * @param clientKey the client's address, plus the endpoint being called, so
     *                  that hammering login does not also lock out registration
     */
    public Decision recordAttempt(String clientKey) {
        if (!properties.enabled()) {
            return Decision.allowed(properties.maxAttempts());
        }

        String key = KEY_PREFIX + clientKey;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return Decision.allowed(properties.maxAttempts());
            }
            if (count == 1L) {
                // First attempt in this window starts the clock. The window is
                // fixed rather than sliding: simpler, and the difference does
                // not matter at this threshold.
                redisTemplate.expire(key, Duration.ofSeconds(properties.windowSeconds()));
            }

            long remaining = Math.max(0, properties.maxAttempts() - count);
            if (count > properties.maxAttempts()) {
                Long ttl = redisTemplate.getExpire(key);
                long retryAfter = ttl == null || ttl < 0 ? properties.windowSeconds() : ttl;
                log.warn("Rate limit exceeded for {} ({} attempts in {}s)",
                        clientKey, count, properties.windowSeconds());
                return Decision.blocked(retryAfter);
            }
            return Decision.allowed(remaining);
        } catch (RuntimeException ex) {
            log.error("Rate limit check failed; allowing the request through", ex);
            return Decision.allowed(properties.maxAttempts());
        }
    }

    /** The outcome of one rate limit check. */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {

        static Decision allowed(long remaining) {
            return new Decision(true, remaining, 0);
        }

        static Decision blocked(long retryAfterSeconds) {
            return new Decision(false, 0, retryAfterSeconds);
        }
    }
}

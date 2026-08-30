package com.clinic.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private AuthRateLimiter limiter(boolean enabled, int maxAttempts, int windowSeconds) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new AuthRateLimiter(redisTemplate,
                new RateLimitProperties(enabled, maxAttempts, windowSeconds));
    }

    @Test
    void allowsAttemptsUpToTheLimit() {
        AuthRateLimiter limiter = limiter(true, 3, 60);
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L);

        assertThat(limiter.recordAttempt("1.2.3.4:/login").allowed()).isTrue();
        assertThat(limiter.recordAttempt("1.2.3.4:/login").allowed()).isTrue();
        assertThat(limiter.recordAttempt("1.2.3.4:/login").allowed()).isTrue();
    }

    @Test
    void blocksTheAttemptAfterTheLimit() {
        AuthRateLimiter limiter = limiter(true, 3, 60);
        when(valueOps.increment(anyString())).thenReturn(4L);
        when(redisTemplate.getExpire(anyString())).thenReturn(42L);

        AuthRateLimiter.Decision decision = limiter.recordAttempt("1.2.3.4:/login");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(42L);
    }

    @Test
    void startsTheWindowClockOnTheFirstAttemptOnly() {
        // Re-expiring on every attempt would let a steady stream of requests
        // push the window forward forever and never reset the count.
        AuthRateLimiter limiter = limiter(true, 5, 60);
        when(valueOps.increment(anyString())).thenReturn(1L);
        limiter.recordAttempt("1.2.3.4:/login");
        verify(redisTemplate).expire(anyString(), any(Duration.class));

        when(valueOps.increment(anyString())).thenReturn(2L);
        limiter.recordAttempt("1.2.3.4:/login");
        // Still only the one call, from the first attempt.
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void reportsHowManyAttemptsRemain() {
        AuthRateLimiter limiter = limiter(true, 10, 60);
        when(valueOps.increment(anyString())).thenReturn(3L);

        assertThat(limiter.recordAttempt("1.2.3.4:/login").remaining()).isEqualTo(7L);
    }

    @Test
    void failsOpenWhenRedisIsUnreachable() {
        // A Redis outage must not lock every user out of the product. Rate
        // limiting slows credential stuffing; it is not what keeps accounts
        // safe, and password hashing keeps working regardless.
        AuthRateLimiter limiter = limiter(true, 3, 60);
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThat(limiter.recordAttempt("1.2.3.4:/login").allowed()).isTrue();
    }

    @Test
    void doesNothingWhenDisabled() {
        AuthRateLimiter limiter = limiter(false, 1, 60);

        assertThat(limiter.recordAttempt("1.2.3.4:/login").allowed()).isTrue();
        assertThat(limiter.recordAttempt("1.2.3.4:/login").allowed()).isTrue();
        verify(valueOps, never()).increment(anyString());
    }
}

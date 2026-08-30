package com.clinic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiting for the authentication endpoints (tech-stack.md 4, Security).
 *
 * <p>The threat is credential stuffing: an attacker with a list of leaked
 * email and password pairs trying them one after another. Limiting attempts per
 * client turns hours of automated guessing into weeks.
 */
@ConfigurationProperties(prefix = "clinic.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int maxAttempts,
        int windowSeconds) {
}

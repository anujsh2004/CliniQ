package com.clinic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret always comes from the environment - it is never
 * committed (API contract 19).
 */
@ConfigurationProperties(prefix = "clinic.jwt")
public record JwtProperties(
        String secret,
        long accessTokenSeconds,
        long refreshTokenSeconds) {
}

package com.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The self-hosted no-show risk service in ml-service/ (API contract 26, v1.6).
 *
 * <p>Disabled by default. Risk is advice to the doctor, not part of the
 * schedule itself, so a machine without the Python service must still start and
 * serve the schedule exactly as before.
 *
 * <pre>
 * clinic:
 *   ml:
 *     enabled: true
 *     base-url: http://localhost:8002
 * </pre>
 */
@ConfigurationProperties(prefix = "clinic.ml")
public record MlProperties(
        boolean enabled,
        String baseUrl,
        Duration timeout) {

    public MlProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8002" : baseUrl;
        // Short, unlike speech. Scoring a day is milliseconds of arithmetic, and
        // the schedule waits on it: a slow model must cost the doctor their
        // risk badges, never their schedule.
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
    }
}

package com.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * The self-hosted speech recognition service (API contract 24, v1.5).
 *
 * <p>Disabled by default. The model is a 2.5 GB download and a machine without
 * it should still start and serve every other endpoint, so voice is opt-in
 * rather than a startup dependency.
 *
 * <pre>
 * clinic:
 *   speech:
 *     enabled: true
 *     base-url: http://localhost:8001
 *     languages: [ hi, ta, en ]
 * </pre>
 */
@ConfigurationProperties(prefix = "clinic.speech")
public record SpeechProperties(
        boolean enabled,
        String baseUrl,
        Duration timeout,
        List<String> languages,
        long maxUploadBytes) {

    public SpeechProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8001" : baseUrl;
        // Generous, because the first request after startup pays for the
        // model's warm-up and a timeout there looks like a broken feature
        // rather than a slow one.
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        languages = languages == null || languages.isEmpty()
                ? List.of("hi", "ta", "en")
                : languages;
        maxUploadBytes = maxUploadBytes <= 0 ? 10L * 1024 * 1024 : maxUploadBytes;
    }
}

package com.clinic.config;

import com.clinic.speech.SpeechClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the speech client, but only when voice is switched on.
 *
 * <p>Guarded by {@code clinic.speech.enabled} so a checkout without the model
 * downloaded still starts and serves everything else. Without the guard the
 * voice endpoints would exist and fail at call time, which is a worse
 * experience than not offering them at all.
 */
@Configuration
@ConditionalOnProperty(prefix = "clinic.speech", name = "enabled", havingValue = "true")
public class SpeechConfig {

    @Bean
    public SpeechClient speechClient(SpeechProperties properties) {
        return new SpeechClient(properties);
    }
}

package com.clinic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Enables the auditing that populates {@code createdAt}/{@code updatedAt} on
 * {@link com.clinic.entity.BaseEntity}. Kept out of the application class so
 * web-layer test slices do not have to bootstrap JPA.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Audit columns are {@code OffsetDateTime}, which the default provider does
     * not produce. The offset comes from the JVM timezone, fixed to
     * Asia/Kolkata (NFR-3).
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}

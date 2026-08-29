package com.clinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables the auditing that populates {@code createdAt}/{@code updatedAt} on
 * {@link com.clinic.entity.BaseEntity}. Kept out of the application class so
 * web-layer test slices do not have to bootstrap JPA.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

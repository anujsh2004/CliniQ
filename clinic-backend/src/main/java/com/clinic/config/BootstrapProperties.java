package com.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The administrator created on an empty database (see {@link AdminBootstrap}).
 *
 * <p>The password comes from the environment like every other secret, and is
 * meant to be changed immediately after the first sign-in.
 */
@ConfigurationProperties(prefix = "clinic.bootstrap")
public record BootstrapProperties(
        String adminName,
        String adminEmail,
        String adminPhone,
        String adminPassword) {

    public BootstrapProperties {
        adminName = adminName == null || adminName.isBlank() ? "Clinic Administrator" : adminName;
        adminPhone = adminPhone == null || adminPhone.isBlank() ? "+910000000000" : adminPhone;
    }
}

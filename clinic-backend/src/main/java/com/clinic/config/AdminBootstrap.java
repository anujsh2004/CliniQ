package com.clinic.config;

import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator on an empty database.
 *
 * <p>Since v1.2 an administrator can only be created by another administrator,
 * which leaves a fresh deployment with no way in at all. This closes that loop:
 * if the database contains no administrator, one is created from configuration.
 *
 * <p>It runs only when there is no administrator, so it cannot be used to
 * resurrect access to a clinic that already has one, and it never changes an
 * existing account's password.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository userRepository,
                                            PasswordEncoder passwordEncoder,
                                            BootstrapProperties properties) {
        return args -> createIfMissing(userRepository, passwordEncoder, properties);
    }

    @Transactional
    void createIfMissing(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         BootstrapProperties properties) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (properties.adminEmail() == null || properties.adminEmail().isBlank()
                || properties.adminPassword() == null || properties.adminPassword().isBlank()) {
            log.warn("No administrator exists and no bootstrap administrator is configured. "
                    + "Set clinic.bootstrap.admin-email and clinic.bootstrap.admin-password, "
                    + "or no one will be able to onboard doctors.");
            return;
        }

        User admin = new User();
        admin.setName(properties.adminName());
        admin.setEmail(properties.adminEmail().trim());
        admin.setPhone(properties.adminPhone());
        admin.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
        admin.setRole(Role.ADMIN);
        userRepository.saveAndFlush(admin);

        log.warn("Created the bootstrap administrator {}. Change its password immediately.",
                admin.getEmail());
    }
}

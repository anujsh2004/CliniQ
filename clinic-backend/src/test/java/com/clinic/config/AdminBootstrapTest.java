package com.clinic.config;

import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The first administrator on an empty database.
 *
 * <p>Since contract v1.2 only an administrator can create another, which leaves
 * a fresh deployment with no way in. This is the loop-closer, and the tests
 * below are mostly about what it must <em>not</em> do: it cannot mint an
 * administrator for a clinic that already has one.
 */
class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AdminBootstrap bootstrap = new AdminBootstrap();

    private BootstrapProperties configured() {
        return new BootstrapProperties("Clinic Administrator", "admin@example.com",
                "+919876543210", "BootstrapPassword123");
    }

    @Test
    void createsAnAdministratorWhenTheDatabaseHasNone() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrap.createIfMissing(userRepository, passwordEncoder, configured());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    void theBootstrapPasswordIsHashedLikeAnyOther() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrap.createIfMissing(userRepository, passwordEncoder, configured());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("BootstrapPassword123");
        assertThat(passwordEncoder.matches("BootstrapPassword123", saved.getValue().getPasswordHash()))
                .isTrue();
    }

    @Test
    void doesNothingWhenAnAdministratorAlreadyExists() {
        // Otherwise anyone who can set an environment variable could mint
        // themselves an administrator on a clinic that is already running.
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap.createIfMissing(userRepository, passwordEncoder, configured());

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNothingWhenNoBootstrapAccountIsConfigured() {
        // A blank configuration must not create an account with an empty
        // password. The application logs the situation instead.
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrap.createIfMissing(userRepository, passwordEncoder,
                new BootstrapProperties(null, "", null, ""));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNothingWhenTheEmailIsSetButThePasswordIsNot() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrap.createIfMissing(userRepository, passwordEncoder,
                new BootstrapProperties(null, "admin@example.com", null, null));

        verify(userRepository, never()).saveAndFlush(any());
    }
}

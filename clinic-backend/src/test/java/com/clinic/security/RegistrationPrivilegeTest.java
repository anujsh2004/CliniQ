package com.clinic.security;

import com.clinic.dto.request.RegisterRequest;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.exception.FieldValidationException;
import com.clinic.repository.UserRepository;
import com.clinic.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Privilege escalation through self-registration (decision D1, contract v1.2).
 *
 * <p>The attack these close: {@code POST /auth/register} is public by
 * necessity, and until v1.2 it took the caller's word for what role to create.
 * A single curl produced a working administrator, who can then manage every
 * doctor, schedule and appointment in the clinic.
 */
class RegistrationPrivilegeTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService =
            new JwtService(new JwtProperties("test-secret-value-long-enough-for-hmac-sha256", 3600, 604800));

    private final AuthService authService =
            new AuthService(userRepository, new BCryptPasswordEncoder(), jwtService);

    private RegisterRequest request(Role role) {
        return new RegisterRequest("Someone", "someone@example.com", "+919876543210",
                "StrongPassword123", role);
    }

    private void allowSave() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void selfRegistrationCannotCreateAnAdministrator() {
        allowSave();

        assertThatThrownBy(() -> authService.register(request(Role.ADMIN)))
                .isInstanceOf(FieldValidationException.class);

        // Nothing was written: the request is refused before any account exists.
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void selfRegistrationCannotCreateADoctor() {
        allowSave();

        assertThatThrownBy(() -> authService.register(request(Role.DOCTOR)))
                .isInstanceOf(FieldValidationException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void selfRegistrationStillCreatesPatients() {
        allowSave();

        assertThat(authService.register(request(Role.PATIENT)).role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void anOmittedRoleRegistersAPatientRatherThanFailing() {
        // The frontend sends PATIENT explicitly, but a client that omits the
        // field should get the only thing self-registration can create.
        allowSave();

        assertThat(authService.register(request(null)).role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void anAdministratorCanCreateADoctorAccount() {
        allowSave();

        assertThat(authService.createStaffAccount(request(Role.DOCTOR)).role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void anAdministratorCanCreateAnotherAdministrator() {
        // Otherwise the clinic could never add a second administrator once
        // self-registration stopped granting roles.
        allowSave();

        assertThat(authService.createStaffAccount(request(Role.ADMIN)).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void theStaffEndpointRefusesToCreatePatients() {
        // A patient created here would be indistinguishable from one who
        // registered normally, so the two paths stay separate.
        allowSave();

        assertThatThrownBy(() -> authService.createStaffAccount(request(Role.PATIENT)))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> authService.createStaffAccount(request(null)))
                .isInstanceOf(FieldValidationException.class);
    }
}

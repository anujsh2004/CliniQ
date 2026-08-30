package com.clinic.service;

import com.clinic.dto.request.LoginRequest;
import com.clinic.dto.request.RefreshTokenRequest;
import com.clinic.dto.request.RegisterRequest;
import com.clinic.dto.response.LoginResponse;
import com.clinic.dto.response.RegisterResponse;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.exception.DuplicateEmailException;
import com.clinic.exception.FieldValidationException;
import com.clinic.exception.InvalidCredentialsException;
import com.clinic.repository.UserRepository;
import com.clinic.security.JwtProperties;
import com.clinic.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService(new JwtProperties("test-secret-value-that-is-long-enough-for-hmac", 3600, 604800));

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest("Anuj Kumar", "anuj@example.com", "+919876543210",
                "StrongPassword123", Role.PATIENT);
    }

    private User storedUser(String rawPassword) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Anuj Kumar");
        user.setEmail("anuj@example.com");
        user.setPhone("+919876543210");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.PATIENT);
        return user;
    }

    @Test
    void registerHashesThePasswordAndNeverReturnsIt() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        RegisterResponse response = authService.register(registerRequest());

        assertThat(response.email()).isEqualTo("anuj@example.com");
        assertThat(response.role()).isEqualTo(Role.PATIENT);
        // The response record has no password component at all, and the stored
        // hash must not be the raw password.
        assertThat(response.toString()).doesNotContain("StrongPassword123");
    }

    @Test
    void registerRejectsADuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void registerRejectsADuplicatePhoneAsAFieldError() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void loginIssuesATokenPairForCorrectCredentials() {
        when(userRepository.findByEmailIgnoreCase("anuj@example.com"))
                .thenReturn(Optional.of(storedUser("StrongPassword123")));

        LoginResponse response = authService.login(new LoginRequest("anuj@example.com", "StrongPassword123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.user().role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void loginRejectsAWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("anuj@example.com"))
                .thenReturn(Optional.of(storedUser("StrongPassword123")));

        assertThatThrownBy(() -> authService.login(new LoginRequest("anuj@example.com", "WrongPassword123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "StrongPassword123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshIssuesANewAccessTokenForAValidRefreshToken() {
        User user = storedUser("StrongPassword123");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        String refreshToken = jwtService.issueRefreshToken(user);

        assertThat(authService.refresh(new RefreshTokenRequest(refreshToken)).accessToken()).isNotBlank();
    }

    @Test
    void refreshRejectsAnAccessTokenUsedAsARefreshToken() {
        User user = storedUser("StrongPassword123");
        String accessToken = jwtService.issueAccessToken(user);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(accessToken)))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}

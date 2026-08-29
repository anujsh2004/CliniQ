package com.clinic.security;

import com.clinic.entity.Role;
import com.clinic.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-value-that-is-long-enough-for-hmac-sha256";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, 3600, 604800));

    private User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Anuj Kumar");
        user.setEmail("anuj@example.com");
        user.setPhone("+919876543210");
        user.setPasswordHash("irrelevant");
        user.setRole(role);
        return user;
    }

    @Test
    void accessTokenCarriesIdentityAndRole() {
        User user = user(Role.PATIENT);

        Optional<AuthenticatedUser> parsed = jwtService.parse(jwtService.issueAccessToken(user), TokenType.ACCESS);

        assertThat(parsed).contains(new AuthenticatedUser(user.getId(), user.getEmail(), Role.PATIENT));
    }

    @Test
    void accessTokenIsRejectedWhereARefreshTokenIsExpected() {
        String accessToken = jwtService.issueAccessToken(user(Role.DOCTOR));

        assertThat(jwtService.parse(accessToken, TokenType.REFRESH)).isEmpty();
    }

    @Test
    void refreshTokenIsRejectedWhereAnAccessTokenIsExpected() {
        String refreshToken = jwtService.issueRefreshToken(user(Role.DOCTOR));

        assertThat(jwtService.parse(refreshToken, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService(
                new JwtProperties("a-completely-different-secret-value-of-sufficient-length", 3600, 604800));
        String foreignToken = other.issueAccessToken(user(Role.ADMIN));

        assertThat(jwtService.parse(foreignToken, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, -1, -1));
        String expired = shortLived.issueAccessToken(user(Role.PATIENT));

        assertThat(jwtService.parse(expired, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(jwtService.parse("not-a-jwt", TokenType.ACCESS)).isEmpty();
    }
}

package com.clinic.dto.response;

/**
 * Login result: the access/refresh pair plus the caller's identity
 * (API contract 8).
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        AuthUserSummary user) {
}

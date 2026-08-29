package com.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/refresh (API contract 8).
 */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken) {
}

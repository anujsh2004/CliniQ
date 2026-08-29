package com.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/login (API contract 8).
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password) {
}

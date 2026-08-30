package com.clinic.dto.response;

/**
 * Refresh result (API contract 8): a new access token only. The refresh token
 * itself is unchanged.
 */
public record TokenRefreshResponse(String accessToken, long expiresIn) {
}

package com.clinic.security;

/**
 * Distinguishes the two tokens issued at login. The type is carried as a claim
 * so an access token cannot be replayed against the refresh endpoint, or the
 * other way round.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}

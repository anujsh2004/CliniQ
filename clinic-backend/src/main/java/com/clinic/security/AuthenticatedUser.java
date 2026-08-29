package com.clinic.security;

import com.clinic.entity.Role;

import java.util.UUID;

/**
 * The authenticated principal placed in the security context by
 * {@link JwtAuthenticationFilter}. Carries only what authorization decisions
 * need: who the caller is and what role they hold.
 */
public record AuthenticatedUser(UUID userId, String email, Role role) {
}

package com.clinic.dto.response;

import com.clinic.entity.Role;

/**
 * Registration result (API contract 8). Deliberately carries no password field
 * of any kind.
 */
public record RegisterResponse(
        String userId,
        String name,
        String email,
        String phone,
        Role role) {
}

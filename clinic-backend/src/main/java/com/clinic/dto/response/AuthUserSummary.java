package com.clinic.dto.response;

import com.clinic.entity.Role;

/**
 * The compact user block embedded in the login response (API contract 8).
 */
public record AuthUserSummary(String userId, String name, Role role) {
}

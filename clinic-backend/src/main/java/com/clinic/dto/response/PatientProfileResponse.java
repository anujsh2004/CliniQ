package com.clinic.dto.response;

/**
 * GET /api/v1/patients/me (API contract 10).
 */
public record PatientProfileResponse(String patientId, String name, String email, String phone) {
}

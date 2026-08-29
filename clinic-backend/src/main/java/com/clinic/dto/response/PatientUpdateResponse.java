package com.clinic.dto.response;

/**
 * PUT /api/v1/patients/me (API contract 10). The contract's update response
 * carries no email field.
 */
public record PatientUpdateResponse(String patientId, String name, String phone) {
}

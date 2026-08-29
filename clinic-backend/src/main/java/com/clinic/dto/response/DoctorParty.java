package com.clinic.dto.response;

/**
 * The doctor block inside an appointment payload (API contract 12). The field
 * is named doctorId, exactly as the contract spells it.
 */
public record DoctorParty(String doctorId, String name) {
}

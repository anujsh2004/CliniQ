package com.clinic.dto.response;

/**
 * Clinic block on a doctor payload (API contract 9). {@code phone} is present
 * on the doctor detail view and absent on the lighter creation response, so it
 * is nullable here and suppressed when null.
 */
public record ClinicSummary(String clinicId, String name, String address, String phone) {

    public static ClinicSummary withoutPhone(String clinicId, String name, String address) {
        return new ClinicSummary(clinicId, name, address, null);
    }
}

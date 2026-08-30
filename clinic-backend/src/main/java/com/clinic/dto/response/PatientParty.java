package com.clinic.dto.response;

/**
 * The patient block inside an appointment payload (API contract 12/13).
 * {@code phone} appears only where the contract shows it - the patient block of
 * a doctor's daily list - and is suppressed elsewhere.
 */
public record PatientParty(String patientId, String name, String phone) {

    public static PatientParty withoutPhone(String patientId, String name) {
        return new PatientParty(patientId, name, null);
    }
}

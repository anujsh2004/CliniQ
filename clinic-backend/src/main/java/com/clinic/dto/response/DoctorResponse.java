package com.clinic.dto.response;

import java.math.BigDecimal;

/**
 * Doctor detail and creation payload (API contract 9), including the clinic
 * block.
 */
public record DoctorResponse(
        String doctorId,
        String name,
        String specialization,
        BigDecimal consultationFee,
        ClinicSummary clinic) {
}

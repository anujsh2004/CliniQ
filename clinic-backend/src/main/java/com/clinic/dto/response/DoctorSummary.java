package com.clinic.dto.response;

import java.math.BigDecimal;

/**
 * Doctor list item (API contract 9). The list view is deliberately lighter than
 * the detail view and carries no clinic block.
 */
public record DoctorSummary(
        String doctorId,
        String name,
        String specialization,
        BigDecimal consultationFee) {
}

package com.clinic.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * POST /api/v1/doctors/{doctorId}/time-off (API contract 11, v1.3).
 *
 * <p>Blocks one date without touching the weekly pattern, which is what a
 * holiday or a conference actually is.
 */
public record TimeOffRequest(

        @NotNull(message = "Date is required")
        LocalDate date,

        String reason) {
}

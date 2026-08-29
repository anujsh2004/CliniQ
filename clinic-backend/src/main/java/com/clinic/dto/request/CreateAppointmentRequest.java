package com.clinic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * POST /api/v1/appointments (API contract 12).
 */
public record CreateAppointmentRequest(

        @NotNull(message = "Doctor id is required")
        UUID doctorId,

        @NotNull(message = "Slot id is required")
        UUID slotId,

        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {
}

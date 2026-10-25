package com.clinic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * POST /api/v1/appointments (API contract 12).
 *
 * <p>{@code symptoms} is optional and added in v1.6 (API contract 27): codes
 * from {@code GET /api/v1/symptoms}. Only the format is checked here, so
 * booking never depends on the ML service being up; a code the model does not
 * know is stored and simply gives no hint.
 */
public record CreateAppointmentRequest(

        @NotNull(message = "Doctor id is required")
        UUID doctorId,

        @NotNull(message = "Slot id is required")
        UUID slotId,

        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason,

        @Size(max = 20, message = "Choose at most 20 symptoms")
        List<@Pattern(regexp = "[a-z0-9_]{1,60}", message = "Unrecognised symptom code") String> symptoms) {

    /** A booking without symptoms, as every booking was before v1.6. */
    public CreateAppointmentRequest(UUID doctorId, UUID slotId, String reason) {
        this(doctorId, slotId, reason, null);
    }
}

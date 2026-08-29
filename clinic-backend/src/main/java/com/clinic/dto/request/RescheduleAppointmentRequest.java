package com.clinic.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * PATCH /api/v1/appointments/{id}/reschedule (API contract 12).
 */
public record RescheduleAppointmentRequest(

        @NotNull(message = "New slot id is required")
        UUID newSlotId) {
}

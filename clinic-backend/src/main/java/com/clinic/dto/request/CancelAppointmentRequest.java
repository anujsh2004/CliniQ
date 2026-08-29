package com.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/appointments/{id}/cancel (API contract 12). The contract makes
 * the reason required.
 */
public record CancelAppointmentRequest(

        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {
}

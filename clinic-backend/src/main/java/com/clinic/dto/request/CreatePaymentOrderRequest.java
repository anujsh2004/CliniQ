package com.clinic.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * POST /api/v1/payments/create-order (API contract 14).
 */
public record CreatePaymentOrderRequest(

        @NotNull(message = "Appointment id is required")
        UUID appointmentId) {
}

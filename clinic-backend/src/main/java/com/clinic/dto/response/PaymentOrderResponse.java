package com.clinic.dto.response;

import com.clinic.entity.PaymentStatus;

import java.math.BigDecimal;

/**
 * POST /api/v1/payments/create-order (API contract 14).
 */
public record PaymentOrderResponse(
        String paymentId,
        String appointmentId,
        String gateway,
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status) {
}

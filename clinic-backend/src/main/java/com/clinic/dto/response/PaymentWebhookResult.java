package com.clinic.dto.response;

import com.clinic.entity.PaymentStatus;

/**
 * The internal result of processing a gateway webhook (API contract 14).
 */
public record PaymentWebhookResult(String paymentId, String appointmentId, PaymentStatus status) {
}

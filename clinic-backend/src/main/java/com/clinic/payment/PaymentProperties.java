package com.clinic.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway credentials, always from the environment (API contract 19).
 *
 * <p>When {@code keyId} is blank the application falls back to the stub
 * gateway, so the whole booking-to-payment flow can be exercised locally
 * without Razorpay credentials.
 */
@ConfigurationProperties(prefix = "clinic.payments.razorpay")
public record PaymentProperties(
        String keyId,
        String keySecret,
        String webhookSecret,
        String apiBaseUrl) {

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}

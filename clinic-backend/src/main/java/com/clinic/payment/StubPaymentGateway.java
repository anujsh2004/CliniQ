package com.clinic.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stands in for Razorpay when no credentials are configured, so the booking to
 * payment flow can be exercised locally and in tests.
 *
 * <p>It signs and verifies webhooks with the same HMAC-SHA256 scheme as the real
 * gateway, so the security-critical path is the one that ships - only the
 * network call to create an order is faked.
 */
public class StubPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGateway.class);

    private final String webhookSecret;

    public StubPaymentGateway(String webhookSecret) {
        this.webhookSecret = webhookSecret;
        log.warn("No payment gateway credentials configured - using the stub gateway. "
                + "Never run this in production.");
    }

    @Override
    public String name() {
        // The contract's payload says RAZORPAY, and the stub stands in for it.
        return "RAZORPAY";
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt) {
        String orderId = "order_stub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        log.info("Stub gateway created order {} for receipt {}", orderId, receipt);
        return new GatewayOrder(orderId, amount, currency);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return SignatureVerifier.matches(rawBody, webhookSecret, signature);
    }
}

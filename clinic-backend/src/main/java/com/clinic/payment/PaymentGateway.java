package com.clinic.payment;

import java.math.BigDecimal;

/**
 * The clinic's view of a payment gateway, kept deliberately small: create an
 * order, and prove that a webhook really came from the gateway.
 *
 * <p>Everything the product guarantees about payments rests on the second
 * method. The client's own report of a successful checkout is never trusted
 * (API contract 14, product-description.md 8.6), so signature verification is
 * the only thing that can move money-related state.
 */
public interface PaymentGateway {

    /** The gateway name recorded on the payment, e.g. RAZORPAY. */
    String name();

    /**
     * Creates an order the client can check out against.
     *
     * @param amount   amount in the major unit (rupees), as the contract shows it
     * @param currency ISO currency code
     * @param receipt  our own reference, so the order can be traced back
     */
    GatewayOrder createOrder(BigDecimal amount, String currency, String receipt);

    /**
     * Verifies that {@code rawBody} was signed by the gateway.
     *
     * @param rawBody   the exact bytes received, before any parsing - re-serialising
     *                  the JSON would change the signature
     * @param signature the signature header the gateway sent
     */
    boolean verifyWebhookSignature(String rawBody, String signature);
}

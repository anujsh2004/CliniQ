package com.clinic.controller;

import com.clinic.dto.request.CreatePaymentOrderRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.PaymentOrderResponse;
import com.clinic.dto.response.PaymentWebhookResult;
import com.clinic.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Payment endpoints (API contract 14).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/create-order")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @Valid @RequestBody CreatePaymentOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Payment order created successfully", paymentService.createOrder(request.appointmentId())));
    }

    /**
     * Called by the gateway, not by our client.
     *
     * <p>The body is taken as a raw string on purpose: the signature covers the
     * exact bytes sent, and letting Jackson parse and re-serialise the JSON
     * first would change them and break verification.
     *
     * <p>An unverified request changes nothing and is answered 401 - it is not
     * from the gateway, whatever it claims.
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<PaymentWebhookResult>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {

        if (!paymentService.isSignatureValid(rawBody, signature)) {
            // Logged and dropped: no state changes, and the reason is not
            // echoed back to whoever sent it.
            log.warn("Rejected a payment webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.success("Webhook rejected", null));
        }

        WebhookEvent event = parse(rawBody);
        if (event == null) {
            return ResponseEntity.ok(ApiResponse.success("Webhook ignored", null));
        }

        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully",
                paymentService.applyVerifiedWebhook(event.orderId(), event.paymentId(), event.captured())));
    }

    /**
     * Razorpay wraps the payment entity in
     * {@code payload.payment.entity}; the event name says what happened.
     * Events we do not act on return null and are acknowledged, so the gateway
     * stops retrying them.
     */
    private WebhookEvent parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventName = root.path("event").asString("");
            JsonNode entity = root.path("payload").path("payment").path("entity");
            String orderId = entity.path("order_id").asString("");

            if (orderId.isBlank()) {
                log.info("Webhook event {} carried no order id; ignoring", eventName);
                return null;
            }
            return switch (eventName) {
                case "payment.captured" -> new WebhookEvent(orderId, entity.path("id").asString(""), true);
                case "payment.failed" -> new WebhookEvent(orderId, entity.path("id").asString(""), false);
                default -> {
                    log.info("Webhook event {} is not one we act on; acknowledging", eventName);
                    yield null;
                }
            };
        } catch (RuntimeException ex) {
            log.warn("Could not read a verified webhook body", ex);
            return null;
        }
    }

    private record WebhookEvent(String orderId, String paymentId, boolean captured) {
    }
}

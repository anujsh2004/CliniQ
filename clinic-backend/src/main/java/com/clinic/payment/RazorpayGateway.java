package com.clinic.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Razorpay, over its REST API (API contract 14).
 */
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final PaymentProperties properties;
    private final RestClient restClient;

    public RazorpayGateway(PaymentProperties properties) {
        this.properties = properties;
        String credentials = Base64.getEncoder().encodeToString(
                (properties.keyId() + ":" + properties.keySecret()).getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .build();
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt) {
        // Razorpay works in the minor unit: 500 rupees is 50000 paise.
        long minorUnits = amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();

        Map<String, Object> response = restClient.post()
                .uri("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", minorUnits,
                        "currency", currency,
                        "receipt", receipt,
                        "payment_capture", 1))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (response == null || response.get("id") == null) {
            throw new PaymentGatewayException("Razorpay did not return an order id");
        }
        log.info("Created Razorpay order {} for receipt {}", response.get("id"), receipt);
        return new GatewayOrder(String.valueOf(response.get("id")), amount, currency);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return SignatureVerifier.matches(rawBody, properties.webhookSecret(), signature);
    }
}

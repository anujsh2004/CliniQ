package com.clinic.payment;

import java.math.BigDecimal;

/** An order as the gateway created it. */
public record GatewayOrder(String orderId, BigDecimal amount, String currency) {
}

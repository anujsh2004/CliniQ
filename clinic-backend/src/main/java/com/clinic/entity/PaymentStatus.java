package com.clinic.entity;

/**
 * Payment states from API contract 17. Tracked on the appointment from the
 * start so the contract's paymentStatus field is honest before the payments
 * phase ships.
 */
public enum PaymentStatus {
    PENDING,
    CREATED,
    PAID,
    FAILED,
    REFUNDED
}

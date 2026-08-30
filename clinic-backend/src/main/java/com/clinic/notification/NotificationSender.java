package com.clinic.notification;

/**
 * Delivers a composed message over one channel.
 *
 * <p>An interface so the worker does not depend on any particular provider:
 * WhatsApp is the only channel the contract names (API contract 15), and
 * SMS or email can be added without touching the worker
 * (product-description.md 22, item 5).
 */
public interface NotificationSender {

    /**
     * @return the provider's own message id, when it gives one
     * @throws NotificationDeliveryException when delivery failed and is worth retrying
     */
    String send(String recipientPhone, String message);
}

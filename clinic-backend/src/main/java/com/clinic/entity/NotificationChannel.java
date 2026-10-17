package com.clinic.entity;

/**
 * Delivery channels.
 *
 * <p>{@code IN_APP} is the one the product uses: reminders appear in the
 * patient's own notification list. WhatsApp remains defined because the
 * contract names it (API contract 15) and the worker already knows how to
 * deliver through a provider, but no provider is configured (decision D19).
 */
public enum NotificationChannel {
    IN_APP,
    WHATSAPP
}

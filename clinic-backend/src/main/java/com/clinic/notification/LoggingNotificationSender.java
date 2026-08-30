package com.clinic.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Stands in for the WhatsApp provider until credentials exist.
 *
 * <p>It logs the message that would have been sent rather than pretending to
 * send it, so a staging run shows exactly what a patient would receive. The
 * whole path around it - queueing, consuming, status transitions, retry, dead
 * lettering - is the real one; only the provider call is missing.
 */
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public String send(String recipientPhone, String message) {
        log.info("[no WhatsApp provider configured] would send to {}: {}", recipientPhone, message);
        return "logged_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

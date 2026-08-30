package com.clinic.config;

import com.clinic.notification.LoggingNotificationSender;
import com.clinic.notification.NotificationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the delivery provider.
 *
 * <p>No WhatsApp Business API credentials exist yet, so the logging sender
 * stands in. Swapping in a real provider is one bean: everything around it -
 * queueing, retry, status transitions, dead lettering - is already the path
 * that will ship.
 */
@Configuration
public class NotificationConfig {

    @Bean
    public NotificationSender notificationSender() {
        return new LoggingNotificationSender();
    }
}

package com.clinic.notification;

import com.clinic.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes reminders to RabbitMQ as JSON.
 *
 * <p>A broker failure is logged and swallowed on purpose. The notification row
 * is already saved as QUEUED, so the reminder is recoverable, and an
 * unreachable broker must not fail the request that triggered it.
 */
@Component
public class RabbitReminderPublisher implements ReminderPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitReminderPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitReminderPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ReminderMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.REMINDER_ROUTING_KEY, payload);
            log.debug("Published reminder {} for appointment {}",
                    message.notificationId(), message.appointmentId());
        } catch (RuntimeException ex) {
            log.error("Could not publish reminder {}; it stays QUEUED for the next sweep",
                    message.notificationId(), ex);
        }
    }
}

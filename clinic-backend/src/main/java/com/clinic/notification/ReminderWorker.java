package com.clinic.notification;

import com.clinic.config.RabbitConfig;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationStatus;
import com.clinic.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Consumes queued reminders and delivers them (product-description.md 13,
 * tech-stack.md 6.1).
 *
 * <p>How failure is handled matters more than the happy path here:
 *
 * <ul>
 *   <li>a message that cannot be parsed is rejected outright - retrying a
 *       malformed message would fail identically four more times before dead
 *       lettering, for nothing;</li>
 *   <li>a delivery failure is rethrown so the listener's backoff retries it,
 *       and the notification is marked FAILED with the reason, so a reminder
 *       that never arrived is visible rather than lost;</li>
 *   <li>a reminder already SENT or DELIVERED is skipped, because the broker
 *       guarantees at-least-once delivery and a patient must not be messaged
 *       twice.</li>
 * </ul>
 */
@Component
public class ReminderWorker {

    private static final Logger log = LoggerFactory.getLogger(ReminderWorker.class);

    private final NotificationRepository notificationRepository;
    private final NotificationSender sender;
    private final ReminderMessageComposer composer;
    private final ObjectMapper objectMapper;

    public ReminderWorker(NotificationRepository notificationRepository,
                          NotificationSender sender,
                          ReminderMessageComposer composer,
                          ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.sender = sender;
        this.composer = composer;
        this.objectMapper = objectMapper;
    }

    /**
     * Deliberately not {@code @Transactional}. Marking a notification FAILED and
     * then rethrowing so the listener retries would roll that write back in a
     * single transaction, and the failure would vanish precisely when it
     * matters. Each save here commits on its own.
     */
    @RabbitListener(queues = RabbitConfig.REMINDER_QUEUE)
    public void onReminder(String payload) {
        ReminderMessage reminder = parse(payload);

        Optional<Notification> stored = notificationRepository.findById(reminder.notificationId());
        if (stored.isEmpty()) {
            // The record is gone; there is nothing to update and no point
            // retrying. Acknowledge and move on.
            log.warn("Reminder {} has no notification record; dropping", reminder.notificationId());
            return;
        }

        Notification notification = stored.get();
        if (notification.getStatus() == NotificationStatus.SENT
                || notification.getStatus() == NotificationStatus.DELIVERED) {
            log.debug("Reminder {} already {}; skipping", notification.getId(), notification.getStatus());
            return;
        }

        try {
            String providerMessageId = sender.send(reminder.patientPhone(), composer.compose(reminder));
            notification.setStatus(NotificationStatus.SENT);
            notification.setFailureReason(null);
            notificationRepository.save(notification);
            log.info("Reminder {} sent for appointment {} (provider id {})",
                    notification.getId(), reminder.appointmentId(), providerMessageId);
        } catch (NotificationDeliveryException ex) {
            // Recorded before rethrowing, so the failure is visible even while
            // the listener is still retrying.
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(truncate(ex.getMessage()));
            notificationRepository.save(notification);
            log.warn("Reminder {} failed to send; will retry", notification.getId(), ex);
            throw ex;
        }
    }

    private ReminderMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, ReminderMessage.class);
        } catch (RuntimeException ex) {
            // Retrying a malformed message just delays the inevitable, so it
            // goes straight to the dead letter queue.
            log.error("Could not read a reminder message; dead lettering it", ex);
            throw new AmqpRejectAndDontRequeueException("Malformed reminder message", ex);
        }
    }

    private String truncate(String reason) {
        if (reason == null) {
            return "Delivery failed";
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}

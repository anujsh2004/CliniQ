package com.clinic.notification;

import com.clinic.entity.Notification;
import com.clinic.entity.NotificationChannel;
import com.clinic.entity.NotificationStatus;
import com.clinic.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reminder delivery, and specifically what happens when it goes wrong
 * (product-description.md 13).
 */
class ReminderWorkerTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReminderWorker worker = new ReminderWorker(
            notificationRepository, sender, new ReminderMessageComposer(), objectMapper);

    private final UUID notificationId = UUID.randomUUID();

    private String payload() {
        return objectMapper.writeValueAsString(new ReminderMessage(
                notificationId, UUID.randomUUID(), UUID.randomUUID(),
                "Anjali Verma", "+919876543210", "Dr. Sharma",
                LocalDate.of(2026, 8, 31), LocalTime.of(10, 0),
                NotificationChannel.WHATSAPP, "24_HOURS"));
    }

    private Notification notification(NotificationStatus status) {
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setStatus(status);
        return notification;
    }

    @Test
    void sendsTheReminderAndMarksItSent() {
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification(NotificationStatus.QUEUED)));
        when(sender.send(anyString(), anyString())).thenReturn("provider_1");

        worker.onReminder(payload());

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getValue().getFailureReason()).isNull();
    }

    @Test
    void theMessageNamesTheDoctorTheDateAndTheTime() {
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification(NotificationStatus.QUEUED)));
        when(sender.send(anyString(), anyString())).thenReturn("provider_1");

        worker.onReminder(payload());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender).send(org.mockito.ArgumentMatchers.eq("+919876543210"), message.capture());
        assertThat(message.getValue())
                .contains("Anjali Verma")
                .contains("Dr. Sharma")
                .contains("Monday 31 August")
                .contains("10:00 AM");
    }

    @Test
    void aDeliveryFailureIsRecordedAndRethrownForRetry() {
        // Recording before rethrowing is what makes a reminder that never
        // arrived visible instead of lost.
        Notification stored = notification(NotificationStatus.QUEUED);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(stored));
        when(sender.send(anyString(), anyString()))
                .thenThrow(new NotificationDeliveryException("provider returned 503"));

        assertThatThrownBy(() -> worker.onReminder(payload()))
                .isInstanceOf(NotificationDeliveryException.class);

        assertThat(stored.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(stored.getFailureReason()).contains("503");
        verify(notificationRepository).save(stored);
    }

    @Test
    void anAlreadySentReminderIsNotSentAgain() {
        // The broker delivers at least once, so a redelivery must not message
        // the patient twice.
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification(NotificationStatus.SENT)));

        worker.onReminder(payload());

        verify(sender, never()).send(anyString(), anyString());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void aReminderWithNoRecordIsDroppedRatherThanRetriedForever() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatCode(() -> worker.onReminder(payload())).doesNotThrowAnyException();
        verify(sender, never()).send(anyString(), anyString());
    }

    @Test
    void aMalformedMessageGoesStraightToTheDeadLetterQueue() {
        // Retrying it would fail identically four more times for nothing.
        assertThatThrownBy(() -> worker.onReminder("this is not json"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(sender, never()).send(anyString(), anyString());
    }
}

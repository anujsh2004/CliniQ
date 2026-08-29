package com.clinic.service;

import com.clinic.dto.request.ReminderEventRequest;
import com.clinic.dto.response.NotificationQueuedResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationChannel;
import com.clinic.entity.NotificationStatus;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.notification.ReminderMessage;
import com.clinic.notification.ReminderPublisher;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Queues appointment reminders (API contract 15, product-description.md 13).
 *
 * <p>The notification row is written first and the message published second, so
 * a reminder always leaves a trace even if the broker is unavailable. Delivery
 * itself is the worker's job.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    /** The one reminder type the contract names. */
    public static final String REMINDER_24_HOURS = "24_HOURS";

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReminderPublisher publisher;

    public ReminderService(NotificationRepository notificationRepository,
                           AppointmentRepository appointmentRepository,
                           ReminderPublisher publisher) {
        this.notificationRepository = notificationRepository;
        this.appointmentRepository = appointmentRepository;
        this.publisher = publisher;
    }

    /**
     * Queues a reminder for one appointment. Idempotent: a second call for the
     * same appointment and reminder type returns the existing record rather
     * than queueing a duplicate message.
     */
    @Transactional
    public NotificationQueuedResponse queueReminder(ReminderEventRequest request) {
        Appointment appointment = appointmentRepository.findWithDetailsById(request.appointmentId())
                .orElseThrow(AppointmentNotFoundException::new);

        String reminderType = request.reminderType() == null ? REMINDER_24_HOURS : request.reminderType();

        var existing = notificationRepository
                .findByAppointmentIdAndReminderType(appointment.getId(), reminderType);
        if (existing.isPresent()) {
            Notification found = existing.get();
            // Already delivered: leave it alone, so a patient is never messaged
            // twice.
            if (found.getStatus() == NotificationStatus.SENT
                    || found.getStatus() == NotificationStatus.DELIVERED) {
                return new NotificationQueuedResponse(found.getId().toString(), found.getStatus());
            }
            // Still QUEUED or FAILED: the record exists but the message never
            // got through - the broker was down, the message was lost, or
            // delivery exhausted its retries. Republish rather than returning a
            // reminder that will never arrive. The worker skips anything
            // already sent, so a duplicate publish is harmless.
            found.setStatus(NotificationStatus.QUEUED);
            found.setFailureReason(null);
            notificationRepository.save(found);
            publisher.publish(messageFor(appointment, found));
            return new NotificationQueuedResponse(found.getId().toString(), found.getStatus());
        }

        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setChannel(request.channel() == null ? NotificationChannel.WHATSAPP : request.channel());
        notification.setReminderType(reminderType);
        notification.setScheduledFor(request.scheduledFor() == null
                ? defaultScheduleFor(appointment)
                : request.scheduledFor());
        notification.setStatus(NotificationStatus.QUEUED);

        Notification saved;
        try {
            saved = notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException ex) {
            // Two schedulers raced; the unique index decided. Return the winner.
            return notificationRepository
                    .findByAppointmentIdAndReminderType(appointment.getId(), reminderType)
                    .map(found -> new NotificationQueuedResponse(found.getId().toString(), found.getStatus()))
                    .orElseThrow(() -> ex);
        }

        publisher.publish(messageFor(appointment, saved));

        return new NotificationQueuedResponse(saved.getId().toString(), saved.getStatus());
    }

    /**
     * Everything the worker needs to compose and send, so delivery never has to
     * read the database.
     */
    private ReminderMessage messageFor(Appointment appointment, Notification notification) {
        return new ReminderMessage(
                notification.getId(),
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getUser().getName(),
                appointment.getPatient().getUser().getPhone(),
                appointment.getDoctor().getName(),
                appointment.getSlot().getDate(),
                appointment.getSlot().getStartTime(),
                notification.getChannel(),
                notification.getReminderType());
    }

    /**
     * Finds appointments happening tomorrow and queues their 24-hour reminders.
     *
     * <p>Runs hourly rather than once a day so an appointment booked late still
     * gets its reminder; the unique index on (appointment, reminder type) makes
     * the repetition harmless.
     */
    @Scheduled(cron = "${clinic.notifications.reminder-cron:0 0 * * * *}")
    @Transactional
    public void queueRemindersForTomorrow() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        List<Appointment> due = appointmentRepository.findActiveOnDate(tomorrow,
                List.of(AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CONFIRMED));

        int queued = 0;
        for (Appointment appointment : due) {
            try {
                queueReminder(new ReminderEventRequest(appointment.getId(), null,
                        NotificationChannel.WHATSAPP, REMINDER_24_HOURS, null));
                queued++;
            } catch (RuntimeException ex) {
                // One appointment failing must not stop the rest of the run.
                log.warn("Could not queue a reminder for appointment {}", appointment.getId(), ex);
            }
        }
        log.info("Reminder sweep for {}: {} appointments, {} reminders queued", tomorrow, due.size(), queued);
    }

    /** 24 hours before the appointment starts, in the clinic's own timezone. */
    private OffsetDateTime defaultScheduleFor(Appointment appointment) {
        return appointment.getSlot().getDate()
                .atTime(appointment.getSlot().getStartTime())
                .atZone(CLINIC_ZONE)
                .minusHours(24)
                .toOffsetDateTime();
    }

    @Transactional(readOnly = true)
    public Notification requireNotification(UUID notificationId) {
        return notificationRepository.findById(notificationId).orElseThrow(AppointmentNotFoundException::new);
    }
}

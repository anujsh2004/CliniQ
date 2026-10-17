package com.clinic.service;

import com.clinic.config.ReminderProperties;
import com.clinic.dto.response.NotificationSummary;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationChannel;
import com.clinic.entity.NotificationStatus;
import com.clinic.exception.ApiException;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.notification.ReminderComposer;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import com.clinic.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Appointment reminders, shown in the patient's own notification list
 * (API contract 15, v1.4).
 *
 * <p>Reminders are scheduled when an appointment is booked and become visible
 * when their time arrives. The clinic's rules come from configuration, so
 * changing when a reminder fires or what it says needs no code change. A
 * patient may add their own on top; they never replace the clinic's.
 */
@Service
public class InAppReminderService {

    private static final Logger log = LoggerFactory.getLogger(InAppReminderService.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReminderProperties reminderProperties;
    private final ReminderComposer composer;

    public InAppReminderService(NotificationRepository notificationRepository,
                                AppointmentRepository appointmentRepository,
                                ReminderProperties reminderProperties,
                                ReminderComposer composer) {
        this.notificationRepository = notificationRepository;
        this.appointmentRepository = appointmentRepository;
        this.reminderProperties = reminderProperties;
        this.composer = composer;
    }

    /**
     * Schedules the clinic's reminders for an appointment.
     *
     * <p>Idempotent per reminder type, so re-running it never doubles a
     * patient's reminders.
     */
    @Transactional
    public void scheduleClinicReminders(Appointment appointment) {
        for (ReminderProperties.Rule rule : reminderProperties.reminders()) {
            OffsetDateTime dueAt = appointmentStart(appointment).minus(rule.offset());
            if (dueAt.isBefore(OffsetDateTime.now())) {
                // Booked closer to the appointment than the reminder's notice
                // period: there is no useful moment left to show it.
                continue;
            }
            save(appointment, rule.type(), dueAt, composer.compose(rule.message(), appointment), false);
        }
    }

    /**
     * Adds a reminder the patient asked for, on top of the clinic's.
     *
     * @param offset how far before the appointment they want it
     */
    @Transactional
    public NotificationSummary addPatientReminder(UUID appointmentId, Duration offset) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireOwnAppointment(appointment);

        if (offset.compareTo(reminderProperties.minimumPatientOffset()) < 0) {
            throw new FieldValidationException("minutesBefore",
                    "Choose at least " + reminderProperties.minimumPatientOffset().toMinutes()
                            + " minutes before the appointment");
        }
        OffsetDateTime dueAt = appointmentStart(appointment).minus(offset);
        if (dueAt.isBefore(OffsetDateTime.now())) {
            throw new FieldValidationException("minutesBefore",
                    "That moment has already passed. Choose a shorter reminder.");
        }

        String type = "CUSTOM_" + offset.toMinutes() + "_MINUTES";
        String message = composer.compose(
                "Reminder: your appointment with {doctor} is at {time} on {date}.", appointment);
        Notification saved = save(appointment, type, dueAt, message, true);
        return toSummary(saved);
    }

    /** A patient may remove a reminder they added; the clinic's are not theirs. */
    @Transactional
    public void removePatientReminder(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireOwnAppointment(notification.getAppointment());

        if (!notification.isPatientRequested()) {
            throw new FieldValidationException("notificationId",
                    "This reminder is set by the clinic and cannot be removed.");
        }
        notificationRepository.delete(notification);
    }

    /** The caller's reminders that are actually due, newest first. */
    @Transactional(readOnly = true)
    public List<NotificationSummary> mine() {
        return notificationRepository.findDeliveredForUser(CurrentUser.require().userId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByAppointmentPatientUserIdAndReadAtIsNullAndStatusNot(
                CurrentUser.require().userId(), NotificationStatus.QUEUED);
    }

    @Transactional
    public void markRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireOwnAppointment(notification.getAppointment());
        notification.setReadAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead() {
        UUID userId = CurrentUser.require().userId();
        List<Notification> unread = notificationRepository.findDeliveredForUser(userId).stream()
                .filter(notification -> notification.getReadAt() == null)
                // A reminder that has not been delivered yet cannot have been
                // read, and marking it read would hide it when it arrives.
                .filter(notification -> notification.getStatus() != NotificationStatus.QUEUED)
                .toList();
        unread.forEach(notification -> notification.setReadAt(OffsetDateTime.now()));
        notificationRepository.saveAll(unread);
    }

    /**
     * Makes due reminders visible.
     *
     * <p>In-app delivery is simply a state change: a reminder becomes SENT when
     * its moment arrives, and the patient's list shows it from then on. There is
     * no provider to fail, which is why this needs no retry or dead lettering.
     */
    @Scheduled(cron = "${clinic.notifications.delivery-cron:0 * * * * *}")
    @Transactional
    public void deliverDueReminders() {
        List<Notification> due = notificationRepository
                .findByStatusAndScheduledForBefore(NotificationStatus.QUEUED, OffsetDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        for (Notification notification : due) {
            AppointmentStatus status = notification.getAppointment().getStatus();
            if (status == AppointmentStatus.CANCELLED) {
                // Reminding someone about an appointment they cancelled is
                // worse than saying nothing.
                notification.setStatus(NotificationStatus.FAILED);
                notification.setFailureReason("Appointment was cancelled");
            } else {
                notification.setStatus(NotificationStatus.SENT);
            }
        }
        notificationRepository.saveAll(due);
        log.info("Delivered {} reminders", due.size());
    }

    private Notification save(Appointment appointment, String type, OffsetDateTime dueAt,
                              String message, boolean patientRequested) {
        var existing = notificationRepository
                .findByAppointmentIdAndReminderType(appointment.getId(), type);
        if (existing.isPresent()) {
            return existing.get();
        }
        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setReminderType(type);
        notification.setScheduledFor(dueAt);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setMessage(message);
        notification.setPatientRequested(patientRequested);
        try {
            return notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException ex) {
            // Two requests raced; the unique index decided.
            return notificationRepository.findByAppointmentIdAndReminderType(appointment.getId(), type)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Reminders queued before messages were stored have none, and a blank line
     * in the patient's list would look like a broken product rather than an old
     * record. They are composed on read instead.
     */
    private String messageOf(Notification notification) {
        if (notification.getMessage() != null && !notification.getMessage().isBlank()) {
            return notification.getMessage();
        }
        return composer.compose("Reminder: your appointment with {doctor} is at {time} on {date}.",
                notification.getAppointment());
    }

    private OffsetDateTime appointmentStart(Appointment appointment) {
        return appointment.getSlot().getDate()
                .atTime(appointment.getSlot().getStartTime())
                .atZone(CLINIC_ZONE)
                .toOffsetDateTime();
    }

    private void requireOwnAppointment(Appointment appointment) {
        var caller = CurrentUser.require();
        if (!appointment.getPatient().getUser().getId().equals(caller.userId())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private NotificationSummary toSummary(Notification notification) {
        return new NotificationSummary(
                notification.getId().toString(),
                notification.getAppointment().getId().toString(),
                messageOf(notification),
                notification.getReminderType(),
                notification.getScheduledFor(),
                notification.getStatus(),
                notification.getReadAt() != null,
                notification.isPatientRequested());
    }
}

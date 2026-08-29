package com.clinic.service;

import com.clinic.dto.request.ReminderEventRequest;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Doctor;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationChannel;
import com.clinic.entity.NotificationStatus;
import com.clinic.entity.Patient;
import com.clinic.entity.Slot;
import com.clinic.entity.User;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.notification.ReminderMessage;
import com.clinic.notification.ReminderPublisher;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reminder queueing (API contract 15, product-description.md 13).
 */
class ReminderServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final ReminderPublisher publisher = mock(ReminderPublisher.class);

    private final ReminderService service =
            new ReminderService(notificationRepository, appointmentRepository, publisher);

    private Appointment appointment(LocalDate date, LocalTime start, AppointmentStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Anjali Verma");
        user.setPhone("+919876543210");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Sharma");

        Slot slot = new Slot();
        slot.setId(UUID.randomUUID());
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(30));

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setStatus(status);
        return appointment;
    }

    private void stubSave() {
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    private ReminderEventRequest requestFor(Appointment appointment) {
        return new ReminderEventRequest(appointment.getId(), null, null, null, null);
    }

    @Test
    void queuesAReminderAndPublishesIt() {
        Appointment appointment = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        stubSave();

        var response = service.queueReminder(requestFor(appointment));

        assertThat(response.status()).isEqualTo(NotificationStatus.QUEUED);
        verify(publisher).publish(any(ReminderMessage.class));
    }

    @Test
    void theQueuedMessageCarriesEverythingTheWorkerNeeds() {
        // The worker must not have to read the database to send a message.
        Appointment appointment = appointment(LocalDate.of(2026, 8, 31), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        stubSave();

        service.queueReminder(requestFor(appointment));

        ArgumentCaptor<ReminderMessage> captor = ArgumentCaptor.forClass(ReminderMessage.class);
        verify(publisher).publish(captor.capture());
        ReminderMessage message = captor.getValue();
        assertThat(message.patientName()).isEqualTo("Anjali Verma");
        assertThat(message.patientPhone()).isEqualTo("+919876543210");
        assertThat(message.doctorName()).isEqualTo("Dr. Sharma");
        assertThat(message.appointmentDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(message.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(message.channel()).isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(message.reminderType()).isEqualTo(ReminderService.REMINDER_24_HOURS);
    }

    @Test
    void schedulesTheReminder24HoursBeforeTheAppointmentInTheClinicTimezone() {
        Appointment appointment = appointment(LocalDate.of(2026, 8, 31), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        stubSave();

        service.queueReminder(requestFor(appointment));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        OffsetDateTime expected = LocalDate.of(2026, 8, 30).atTime(10, 0)
                .atZone(CLINIC_ZONE).toOffsetDateTime();
        assertThat(captor.getValue().getScheduledFor()).isEqualTo(expected);
    }

    @Test
    void doesNotSendAnAlreadyDeliveredReminderAgain() {
        // The sweep runs hourly; a patient must not be messaged once an hour.
        Appointment appointment = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        Notification existing = new Notification();
        existing.setId(UUID.randomUUID());
        existing.setStatus(NotificationStatus.SENT);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(
                appointment.getId(), ReminderService.REMINDER_24_HOURS))
                .thenReturn(Optional.of(existing));

        var response = service.queueReminder(requestFor(appointment));

        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        verify(notificationRepository, never()).saveAndFlush(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void republishesAReminderThatIsStillQueued() {
        // The record exists but the message never got through - broker down, or
        // the message was lost. Returning the record unchanged would leave a
        // reminder that can never arrive, so it is published again.
        Appointment appointment = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        Notification stuck = new Notification();
        stuck.setId(UUID.randomUUID());
        stuck.setStatus(NotificationStatus.QUEUED);
        stuck.setChannel(NotificationChannel.WHATSAPP);
        stuck.setReminderType(ReminderService.REMINDER_24_HOURS);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(
                appointment.getId(), ReminderService.REMINDER_24_HOURS))
                .thenReturn(Optional.of(stuck));

        var response = service.queueReminder(requestFor(appointment));

        assertThat(response.status()).isEqualTo(NotificationStatus.QUEUED);
        verify(publisher).publish(any(ReminderMessage.class));
        // No second record: the existing one is reused.
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void retriesAReminderThatPreviouslyFailed() {
        Appointment appointment = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        Notification failed = new Notification();
        failed.setId(UUID.randomUUID());
        failed.setStatus(NotificationStatus.FAILED);
        failed.setFailureReason("provider returned 503");
        failed.setChannel(NotificationChannel.WHATSAPP);
        failed.setReminderType(ReminderService.REMINDER_24_HOURS);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(
                appointment.getId(), ReminderService.REMINDER_24_HOURS))
                .thenReturn(Optional.of(failed));

        var response = service.queueReminder(requestFor(appointment));

        assertThat(response.status()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(failed.getFailureReason()).isNull();
        verify(publisher).publish(any(ReminderMessage.class));
    }

    @Test
    void theNotificationRowIsWrittenBeforeThePublishAttempt() {
        // Ordering is what makes a reminder recoverable: if publishing fails,
        // the row already exists as QUEUED. Swallowing the broker error is the
        // publisher's job (see RabbitReminderPublisherTest), not this one's.
        Appointment appointment = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        stubSave();
        doThrow(new RuntimeException("broker down")).when(publisher).publish(any());

        assertThatThrownBy(() -> service.queueReminder(requestFor(appointment)));

        var inOrder = org.mockito.Mockito.inOrder(notificationRepository, publisher);
        inOrder.verify(notificationRepository).saveAndFlush(any(Notification.class));
        inOrder.verify(publisher).publish(any());
    }

    @Test
    void aReminderForAnUnknownAppointmentIs404() {
        when(appointmentRepository.findWithDetailsById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.queueReminder(
                new ReminderEventRequest(UUID.randomUUID(), null, null, null, null)))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void theSweepQueuesRemindersForTomorrowsAppointments() {
        Appointment first = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        Appointment second = appointment(LocalDate.now().plusDays(1), LocalTime.of(11, 0),
                AppointmentStatus.PENDING_PAYMENT);
        when(appointmentRepository.findActiveOnDate(any(), any())).thenReturn(List.of(first, second));
        when(appointmentRepository.findWithDetailsById(first.getId())).thenReturn(Optional.of(first));
        when(appointmentRepository.findWithDetailsById(second.getId())).thenReturn(Optional.of(second));
        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        stubSave();

        service.queueRemindersForTomorrow();

        verify(publisher, org.mockito.Mockito.times(2)).publish(any());
    }

    @Test
    void oneFailingAppointmentDoesNotStopTheRestOfTheSweep() {
        Appointment broken = appointment(LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                AppointmentStatus.CONFIRMED);
        Appointment healthy = appointment(LocalDate.now().plusDays(1), LocalTime.of(11, 0),
                AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findActiveOnDate(any(), any())).thenReturn(List.of(broken, healthy));
        when(appointmentRepository.findWithDetailsById(broken.getId())).thenReturn(Optional.empty());
        when(appointmentRepository.findWithDetailsById(healthy.getId())).thenReturn(Optional.of(healthy));
        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        stubSave();

        service.queueRemindersForTomorrow();

        verify(publisher, org.mockito.Mockito.times(1)).publish(any());
    }
}

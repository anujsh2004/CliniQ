package com.clinic.service;

import com.clinic.config.ReminderProperties;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationStatus;
import com.clinic.entity.Patient;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.FieldValidationException;
import com.clinic.notification.ReminderComposer;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reminders shown in the patient's own list (API contract 15, v1.4).
 *
 * <p>The rules come from configuration so a clinic can change when a reminder
 * fires and what it says without a code change, and a patient can add their own
 * on top without disturbing the clinic's.
 */
class InAppReminderServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);

    private final ReminderProperties properties = new ReminderProperties(
            List.of(new ReminderProperties.Rule(Duration.ofHours(24),
                            "Appointment with {doctor} tomorrow at {time}."),
                    new ReminderProperties.Rule(Duration.ofHours(1),
                            "Leave soon - your appointment with {doctor} is at {time}.")),
            Duration.ofMinutes(15));

    private final InAppReminderService service = new InAppReminderService(
            notificationRepository, appointmentRepository, properties, new ReminderComposer());

    private UUID patientUserId;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        patientUserId = UUID.randomUUID();

        User user = new User();
        user.setId(patientUserId);
        user.setName("Anjali Verma");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);

        Clinic clinic = new Clinic();
        clinic.setName("Sharma Dental Clinic");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Sharma");
        doctor.setClinic(clinic);

        Slot slot = new Slot();
        slot.setId(UUID.randomUUID());
        slot.setDate(LocalDate.now().plusDays(5));
        slot.setStartTime(LocalTime.of(17, 0));
        slot.setEndTime(LocalTime.of(17, 30));

        appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);

        when(notificationRepository.findByAppointmentIdAndReminderType(any(), any()))
                .thenReturn(Optional.empty());
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(patientUserId, "anjali@example.com", Role.PATIENT), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private List<Notification> scheduledReminders() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void schedulesOneReminderPerConfiguredRule() {
        service.scheduleClinicReminders(appointment);

        assertThat(scheduledReminders()).hasSize(2);
    }

    @Test
    void theMessageIsFilledInFromTheAppointment() {
        // A clinic changing the wording in configuration should see exactly
        // that wording reach the patient.
        service.scheduleClinicReminders(appointment);

        assertThat(scheduledReminders())
                .extracting(Notification::getMessage)
                .anySatisfy(message -> assertThat(message)
                        .isEqualTo("Appointment with Dr. Sharma tomorrow at 5:00 PM."));
    }

    @Test
    void eachReminderIsScheduledForItsOwnOffset() {
        service.scheduleClinicReminders(appointment);

        List<Notification> reminders = scheduledReminders();
        OffsetDateTime start = appointment.getSlot().getDate()
                .atTime(appointment.getSlot().getStartTime())
                .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .toOffsetDateTime();
        assertThat(reminders).extracting(Notification::getScheduledFor)
                .containsExactlyInAnyOrder(start.minusHours(24), start.minusHours(1));
    }

    @Test
    void changingConfigurationChangesWhatIsScheduled() {
        // The point of the feature: no code change to add a third reminder.
        ReminderProperties threeRules = new ReminderProperties(
                List.of(new ReminderProperties.Rule(Duration.ofHours(48), "Two days to go."),
                        new ReminderProperties.Rule(Duration.ofHours(24), "Tomorrow."),
                        new ReminderProperties.Rule(Duration.ofMinutes(30), "Half an hour.")),
                Duration.ofMinutes(15));
        InAppReminderService configured = new InAppReminderService(
                notificationRepository, appointmentRepository, threeRules, new ReminderComposer());

        configured.scheduleClinicReminders(appointment);

        assertThat(scheduledReminders()).hasSize(3);
    }

    @Test
    void aReminderWhoseMomentHasAlreadyPassedIsNotScheduled() {
        // Booked an hour before the appointment: the 24-hour reminder has no
        // useful moment left, so scheduling it would only produce noise.
        appointment.getSlot().setDate(LocalDate.now());
        appointment.getSlot().setStartTime(LocalTime.now().plusMinutes(90));

        service.scheduleClinicReminders(appointment);

        assertThat(scheduledReminders()).hasSize(1);
    }

    @Test
    void aPatientCanAddTheirOwnReminder() {
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));

        var added = service.addPatientReminder(appointment.getId(), Duration.ofHours(3));

        assertThat(added.patientRequested()).isTrue();
        assertThat(added.reminderType()).isEqualTo("CUSTOM_180_MINUTES");
    }

    @Test
    void aPatientReminderTooCloseToTheAppointmentIsRefused() {
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.addPatientReminder(appointment.getId(), Duration.ofMinutes(5)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void aPatientCannotAddAReminderToSomeoneElsesAppointment() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), "other@example.com", Role.PATIENT), null, List.of()));
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.addPatientReminder(appointment.getId(), Duration.ofHours(3)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aPatientCannotRemoveAReminderTheClinicSet() {
        Notification clinicReminder = new Notification();
        clinicReminder.setId(UUID.randomUUID());
        clinicReminder.setAppointment(appointment);
        clinicReminder.setPatientRequested(false);
        when(notificationRepository.findById(clinicReminder.getId()))
                .thenReturn(Optional.of(clinicReminder));

        assertThatThrownBy(() -> service.removePatientReminder(clinicReminder.getId()))
                .isInstanceOf(FieldValidationException.class);
        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void aPatientCanRemoveTheirOwnReminder() {
        Notification own = new Notification();
        own.setId(UUID.randomUUID());
        own.setAppointment(appointment);
        own.setPatientRequested(true);
        when(notificationRepository.findById(own.getId())).thenReturn(Optional.of(own));

        service.removePatientReminder(own.getId());

        verify(notificationRepository).delete(own);
    }

    @Test
    void aDueReminderBecomesVisible() {
        Notification due = new Notification();
        due.setAppointment(appointment);
        due.setStatus(NotificationStatus.QUEUED);
        when(notificationRepository.findByStatusAndScheduledForBefore(any(), any()))
                .thenReturn(List.of(due));

        service.deliverDueReminders();

        assertThat(due.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void aReminderForACancelledAppointmentIsNotShown() {
        // Reminding someone about an appointment they cancelled is worse than
        // saying nothing.
        appointment.setStatus(AppointmentStatus.CANCELLED);
        Notification due = new Notification();
        due.setAppointment(appointment);
        due.setStatus(NotificationStatus.QUEUED);
        when(notificationRepository.findByStatusAndScheduledForBefore(any(), any()))
                .thenReturn(List.of(due));

        service.deliverDueReminders();

        assertThat(due.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(due.getFailureReason()).contains("cancelled");
    }
}

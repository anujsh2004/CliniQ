package com.clinic.ml;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationChannel;
import com.clinic.entity.NotificationStatus;
import com.clinic.entity.Patient;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.entity.User;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.NotificationRepository;
import com.clinic.repository.PatientAttendance;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The history queries behind no-show risk, against a real PostgreSQL.
 *
 * <p>The unit tests mock these queries away, so a wrong JPQL aggregate - a
 * cancellation counted as a visit, a same-day outcome leaking in - would pass
 * them and quietly skew every score. Only a real database shows what the
 * queries count.
 */
@SpringBootTest
@Testcontainers
class NoShowHistoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final LocalDate DAY = LocalDate.of(2026, 8, 20);

    @MockitoBean
    private com.clinic.service.SlotGenerationService slotGenerationService;

    @MockitoBean
    private com.clinic.notification.ReminderPublisher reminderPublisher;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ClinicRepository clinicRepository;

    @Autowired
    private UserRepository userRepository;

    private Doctor doctor;
    private Patient patient;
    private int slotMinute;

    @BeforeEach
    void doctorAndPatient() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Clinic clinic = new Clinic();
        clinic.setName("Risk Clinic " + unique);
        clinic.setAddress("MG Road, Bengaluru");
        clinic.setPhone("+919876543210");
        clinic = clinicRepository.saveAndFlush(clinic);

        User doctorUser = user("riskdoc-" + unique, "+9193" + digits(unique, '3'), Role.DOCTOR);
        doctor = new Doctor();
        doctor.setName("Dr. Risk " + unique);
        doctor.setSpecialization("General");
        doctor.setLicenseNumber("LIC-RISK-" + unique);
        doctor.setConsultationFee(new BigDecimal("500.00"));
        doctor.setClinic(clinic);
        doctor.setUser(doctorUser);
        doctor = doctorRepository.saveAndFlush(doctor);

        patient = new Patient();
        patient.setUser(user("riskpat-" + unique, "+9192" + digits(unique, '4'), Role.PATIENT));
        patient = patientRepository.saveAndFlush(patient);
    }

    private static String digits(String unique, char filler) {
        return unique.replaceAll("[^0-9]", String.valueOf(filler)).substring(0, 8);
    }

    private User user(String email, String phone, Role role) {
        User user = new User();
        user.setName(email);
        user.setEmail(email + "@example.com");
        user.setPhone(phone);
        user.setPasswordHash("irrelevant");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Appointment appointment(LocalDate date, AppointmentStatus status) {
        Slot slot = new Slot();
        slot.setDoctor(doctor);
        slot.setDate(date);
        // Distinct start times, so same-day slots do not collide.
        slot.setStartTime(LocalTime.of(9, 0).plusMinutes(30L * slotMinute++));
        slot.setEndTime(slot.getStartTime().plusMinutes(30));
        slot.setStatus(SlotStatus.BOOKED);
        slot = slotRepository.saveAndFlush(slot);

        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setSlot(slot);
        appointment.setStatus(status);
        appointment.setPaymentStatus(PaymentStatus.PAID);
        return appointmentRepository.saveAndFlush(appointment);
    }

    private void reminder(Appointment appointment, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setReminderType("24_HOURS");
        notification.setScheduledFor(OffsetDateTime.now());
        notification.setStatus(status);
        notification.setMessage("Appointment tomorrow.");
        notificationRepository.saveAndFlush(notification);
    }

    @Test
    void countsOnlyEarlierVisitsWithAKnownOutcome() {
        appointment(DAY.minusDays(10), AppointmentStatus.COMPLETED);
        appointment(DAY.minusDays(5), AppointmentStatus.NO_SHOW);
        // A cancellation is not a visit.
        appointment(DAY.minusDays(3), AppointmentStatus.CANCELLED);
        // Same day: its outcome is not known when the day starts.
        appointment(DAY, AppointmentStatus.COMPLETED);
        // Later: not history at all.
        appointment(DAY.plusDays(2), AppointmentStatus.NO_SHOW);

        List<PatientAttendance> attendance = appointmentRepository.attendanceBefore(
                List.of(patient.getId()), DAY,
                List.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW));

        assertThat(attendance).hasSize(1);
        assertThat(attendance.getFirst().getPatientId()).isEqualTo(patient.getId());
        assertThat(attendance.getFirst().getVisits()).isEqualTo(2);
        assertThat(attendance.getFirst().getNoShows()).isEqualTo(1);
    }

    @Test
    void aFirstTimePatientHasNoRow() {
        appointment(DAY, AppointmentStatus.CONFIRMED);

        assertThat(appointmentRepository.attendanceBefore(List.of(patient.getId()), DAY,
                List.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW))).isEmpty();
    }

    @Test
    void aFailedReminderDoesNotCountAsSent() {
        Appointment queued = appointment(DAY, AppointmentStatus.CONFIRMED);
        Appointment failed = appointment(DAY, AppointmentStatus.CONFIRMED);
        Appointment none = appointment(DAY, AppointmentStatus.CONFIRMED);
        reminder(queued, NotificationStatus.QUEUED);
        reminder(failed, NotificationStatus.FAILED);

        List<UUID> reminded = notificationRepository.findAppointmentIdsWithReminders(
                List.of(queued.getId(), failed.getId(), none.getId()));

        assertThat(reminded).containsExactly(queued.getId());
    }
}

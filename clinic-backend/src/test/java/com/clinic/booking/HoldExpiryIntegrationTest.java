package com.clinic.booking;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.entity.User;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.repository.UserRepository;
import com.clinic.service.HoldExpiryService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hold sweep against a real database and a real Spring context.
 *
 * <p>Written because the unit tests could not catch the bug that shipped here.
 * The sweep called its own {@code @Transactional} release method on
 * {@code this}, which bypasses Spring's proxy, so the pessimistic lock ran with
 * no transaction and every release failed with "No active transaction". A unit
 * test constructs the service directly, so there is no proxy and no transaction
 * to be missing - it passed while the feature was completely broken.
 *
 * <p>Only a wired-up context can see that, which is exactly what this is.
 */
@SpringBootTest
@Testcontainers
class HoldExpiryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** Scheduled slot generation is irrelevant here and must not interfere. */
    @MockitoBean
    private com.clinic.service.SlotGenerationService slotGenerationService;

    @MockitoBean
    private com.clinic.notification.ReminderPublisher reminderPublisher;

    @Autowired
    private HoldExpiryService holdExpiryService;

    @Autowired
    private AppointmentRepository appointmentRepository;

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

    private Appointment heldAppointment(OffsetDateTime holdExpiry, LocalDate date, LocalTime time) {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        Clinic clinic = new Clinic();
        clinic.setName("Hold Clinic " + unique);
        clinic.setAddress("MG Road, Chennai");
        clinic.setPhone("+919876543210");
        clinic = clinicRepository.saveAndFlush(clinic);

        User doctorUser = new User();
        doctorUser.setName("Dr. Hold " + unique);
        doctorUser.setEmail("holddoc-" + unique + "@example.com");
        doctorUser.setPhone("+9195" + unique.replaceAll("[^0-9]", "1").substring(0, 8));
        doctorUser.setPasswordHash("irrelevant");
        doctorUser.setRole(Role.DOCTOR);
        doctorUser = userRepository.saveAndFlush(doctorUser);

        Doctor doctor = new Doctor();
        doctor.setName("Dr. Hold " + unique);
        doctor.setSpecialization("Dentist");
        doctor.setLicenseNumber("LIC-HOLD-" + unique);
        doctor.setConsultationFee(new BigDecimal("500.00"));
        doctor.setClinic(clinic);
        doctor.setUser(doctorUser);
        doctor = doctorRepository.saveAndFlush(doctor);

        User patientUser = new User();
        patientUser.setName("Hold Patient " + unique);
        patientUser.setEmail("holdpat-" + unique + "@example.com");
        patientUser.setPhone("+9194" + unique.replaceAll("[^0-9]", "2").substring(0, 8));
        patientUser.setPasswordHash("irrelevant");
        patientUser.setRole(Role.PATIENT);
        patientUser = userRepository.saveAndFlush(patientUser);

        Patient patient = new Patient();
        patient.setUser(patientUser);
        patient = patientRepository.saveAndFlush(patient);

        Slot slot = new Slot();
        slot.setDoctor(doctor);
        slot.setDate(date);
        slot.setStartTime(time);
        slot.setEndTime(time.plusMinutes(30));
        slot.setStatus(SlotStatus.HELD);
        slot = slotRepository.saveAndFlush(slot);

        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);
        appointment.setPaymentStatus(PaymentStatus.PENDING);
        appointment.setHoldExpiresAt(holdExpiry);
        return appointmentRepository.saveAndFlush(appointment);
    }

    @Test
    void theSweepActuallyReleasesAnExpiredHold() {
        Appointment appointment = heldAppointment(OffsetDateTime.now().minusMinutes(1),
                LocalDate.now().plusDays(5), LocalTime.of(10, 0));

        holdExpiryService.releaseExpiredHolds();

        Slot slot = slotRepository.findById(appointment.getSlot().getId()).orElseThrow();
        Appointment after = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(after.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(after.getHoldExpiresAt()).isNull();
    }

    @Test
    void aHoldThatHasNotRunOutSurvivesTheSweep() {
        Appointment appointment = heldAppointment(OffsetDateTime.now().plusMinutes(5),
                LocalDate.now().plusDays(5), LocalTime.of(11, 0));

        holdExpiryService.releaseExpiredHolds();

        Slot slot = slotRepository.findById(appointment.getSlot().getId()).orElseThrow();
        Appointment after = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.HELD);
        assertThat(after.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
    }

    @Test
    void anExpiredSlotInThePastIsNotPutBackOnSale() {
        Appointment appointment = heldAppointment(OffsetDateTime.now().minusMinutes(1),
                LocalDate.now().minusDays(1), LocalTime.of(9, 0));

        holdExpiryService.releaseExpiredHolds();

        Slot slot = slotRepository.findById(appointment.getSlot().getId()).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.EXPIRED);
    }
}

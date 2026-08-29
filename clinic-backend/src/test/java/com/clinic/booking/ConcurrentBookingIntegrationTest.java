package com.clinic.booking;

import com.clinic.dto.request.CancelAppointmentRequest;
import com.clinic.dto.request.CreateAppointmentRequest;
import com.clinic.dto.request.RescheduleAppointmentRequest;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.entity.User;
import com.clinic.exception.SlotAlreadyBookedException;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.repository.UserRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.service.AppointmentBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product's one non-negotiable guarantee: a slot is never booked twice
 * (product-description.md G1, NFR-1, and the acceptance criterion in section 21
 * that two simultaneous bookings must produce exactly one 201 and one 409).
 *
 * <p>This runs against a real PostgreSQL because that is the only place the
 * guarantee actually lives: {@code SELECT ... FOR UPDATE} and the partial
 * unique index have no meaning on an in-memory database, so a test that mocked
 * the repository would prove nothing about the race.
 */
@SpringBootTest
@Testcontainers
class ConcurrentBookingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AppointmentBookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private ClinicRepository clinicRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    /** The scheduled slot-generation job is irrelevant here and must not run. */
    @MockitoBean
    private com.clinic.service.SlotGenerationService slotGenerationService;

    private Doctor doctor;
    private User firstPatientUser;
    private User secondPatientUser;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        patientRepository.deleteAll();
        doctorRepository.deleteAll();
        clinicRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();

        Clinic clinic = new Clinic();
        clinic.setName("Sharma Dental Clinic");
        clinic.setAddress("MG Road, Chennai");
        clinic.setPhone("+919876543210");
        clinic = clinicRepository.save(clinic);

        User doctorUser = user("Dr. Sharma", "sharma@example.com", "+919000000001", Role.DOCTOR);
        doctor = new Doctor();
        doctor.setUser(doctorUser);
        doctor.setClinic(clinic);
        doctor.setName("Dr. Sharma");
        doctor.setSpecialization("Dentist");
        doctor.setLicenseNumber("LIC-" + UUID.randomUUID());
        doctor.setConsultationFee(new BigDecimal("500.00"));
        doctor = doctorRepository.save(doctor);

        firstPatientUser = patientWithProfile("Anjali Verma", "anjali@example.com", "+919000000002");
        secondPatientUser = patientWithProfile("Ravi Kumar", "ravi@example.com", "+919000000003");
    }

    private User user(String name, String email, String phone, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash("irrelevant-for-this-test");
        user.setRole(role);
        return userRepository.save(user);
    }

    private User patientWithProfile(String name, String email, String phone) {
        User user = user(name, email, phone, Role.PATIENT);
        Patient patient = new Patient();
        patient.setUser(user);
        patientRepository.save(patient);
        return user;
    }

    private Slot availableSlot(LocalTime start) {
        Slot slot = new Slot();
        slot.setDoctor(doctor);
        slot.setDate(LocalDate.now().plusDays(3));
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(30));
        slot.setStatus(SlotStatus.AVAILABLE);
        return slotRepository.save(slot);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole()), null, List.of()));
    }

    /**
     * Runs one booking attempt on its own thread with its own security context,
     * released simultaneously with the others by the latch.
     */
    private Callable<Outcome> bookingAttempt(User patientUser, UUID slotId, CountDownLatch startLine) {
        return () -> {
            authenticateAs(patientUser);
            startLine.await(5, TimeUnit.SECONDS);
            try {
                bookingService.create(new CreateAppointmentRequest(doctor.getId(), slotId, "Dental check-up"));
                return Outcome.BOOKED;
            } catch (SlotAlreadyBookedException ex) {
                return Outcome.REJECTED_AS_ALREADY_BOOKED;
            } catch (RuntimeException ex) {
                return Outcome.REJECTED_FOR_ANOTHER_REASON;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private enum Outcome {
        BOOKED,
        REJECTED_AS_ALREADY_BOOKED,
        REJECTED_FOR_ANOTHER_REASON
    }

    @Test
    void twoSimultaneousBookingsForOneSlotProduceExactlyOneAppointment() throws Exception {
        Slot slot = availableSlot(LocalTime.of(10, 0));
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Outcome> first = pool.submit(bookingAttempt(firstPatientUser, slot.getId(), startLine));
            Future<Outcome> second = pool.submit(bookingAttempt(secondPatientUser, slot.getId(), startLine));

            startLine.countDown();
            List<Outcome> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(
                    Outcome.BOOKED, Outcome.REJECTED_AS_ALREADY_BOOKED);
        } finally {
            pool.shutdownNow();
        }

        assertThat(appointmentRepository.findAll()).hasSize(1);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void tenSimultaneousBookingsForOneSlotStillProduceExactlyOneAppointment() throws Exception {
        // Two threads can pass by luck; ten make a missing lock obvious.
        Slot slot = availableSlot(LocalTime.of(11, 0));
        int attempts = 10;
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try {
            List<Future<Outcome>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                User patientUser = i % 2 == 0 ? firstPatientUser : secondPatientUser;
                futures.add(pool.submit(bookingAttempt(patientUser, slot.getId(), startLine)));
            }

            startLine.countDown();
            for (Future<Outcome> future : futures) {
                switch (future.get(30, TimeUnit.SECONDS)) {
                    case BOOKED -> booked.incrementAndGet();
                    case REJECTED_AS_ALREADY_BOOKED -> rejected.incrementAndGet();
                    case REJECTED_FOR_ANOTHER_REASON -> { /* counted by neither */ }
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(booked.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(attempts - 1);
        assertThat(appointmentRepository.findAll()).hasSize(1);
    }

    @Test
    void cancellingReleasesTheSlotSoAnotherPatientCanTakeIt() {
        Slot slot = availableSlot(LocalTime.of(12, 0));

        authenticateAs(firstPatientUser);
        var created = bookingService.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Dental check-up"));
        bookingService.cancel(UUID.fromString(created.appointmentId()),
                new CancelAppointmentRequest("Personal reason"));
        SecurityContextHolder.clearContext();

        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.AVAILABLE);

        authenticateAs(secondPatientUser);
        var rebooked = bookingService.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Cleaning"));
        SecurityContextHolder.clearContext();

        assertThat(rebooked.status()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
        // The cancelled appointment and the new one coexist; only one is live,
        // which is exactly what the partial unique index permits.
        assertThat(appointmentRepository.findAll()).hasSize(2);
        assertThat(appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus().isActive())).hasSize(1);
    }

    @Test
    void reschedulingReleasesTheOldSlotAndBooksTheNewOneAtomically() {
        Slot original = availableSlot(LocalTime.of(13, 0));
        Slot target = availableSlot(LocalTime.of(14, 0));

        authenticateAs(firstPatientUser);
        var created = bookingService.create(
                new CreateAppointmentRequest(doctor.getId(), original.getId(), "Dental check-up"));
        var rescheduled = bookingService.reschedule(UUID.fromString(created.appointmentId()),
                new RescheduleAppointmentRequest(target.getId()));
        SecurityContextHolder.clearContext();

        assertThat(rescheduled.startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(slotRepository.findById(original.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slotRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);

        Appointment stored = appointmentRepository.findAll().getFirst();
        assertThat(stored.getSlot().getId()).isEqualTo(target.getId());
    }

    @Test
    void aSecondPatientCannotTakeAnAlreadyBookedSlot() {
        Slot slot = availableSlot(LocalTime.of(15, 0));

        authenticateAs(firstPatientUser);
        bookingService.create(new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Dental check-up"));
        SecurityContextHolder.clearContext();

        authenticateAs(secondPatientUser);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingService.create(
                            new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Cleaning")))
                    .isInstanceOf(SlotAlreadyBookedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(appointmentRepository.findAll()).hasSize(1);
    }
}

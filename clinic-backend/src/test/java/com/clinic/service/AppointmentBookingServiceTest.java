package com.clinic.service;

import com.clinic.dto.request.CancelAppointmentRequest;
import com.clinic.dto.request.CreateAppointmentRequest;
import com.clinic.dto.request.RescheduleAppointmentRequest;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.exception.SlotAlreadyBookedException;
import com.clinic.exception.SlotNotFoundException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rejection paths on the booking write path. The happy paths and the race
 * itself are covered against a real PostgreSQL in
 * {@code ConcurrentBookingIntegrationTest}; these cases are about which
 * canonical error each unbookable situation maps to.
 */
class AppointmentBookingServiceTest {

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final SlotRepository slotRepository = mock(SlotRepository.class);
    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final PatientService patientService = mock(PatientService.class);

    private final AppointmentBookingService service = new AppointmentBookingService(
            appointmentRepository, slotRepository, doctorRepository, patientService, new AppointmentMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "caller@example.com", role), null, List.of()));
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setName("Anjali Verma");
        user.setPhone("+919876543210");
        return user;
    }

    private Doctor doctor() {
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Sharma");
        return doctor;
    }

    private Patient patient(UUID userId) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user(userId));
        return patient;
    }

    private Slot slot(Doctor doctor, SlotStatus status, LocalDate date) {
        Slot slot = new Slot();
        slot.setId(UUID.randomUUID());
        slot.setDoctor(doctor);
        slot.setDate(date);
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setStatus(status);
        return slot;
    }

    private void stubBookingContext(Doctor doctor, Slot slot, UUID patientUserId) {
        when(patientService.requireOwnPatient()).thenReturn(patient(patientUserId));
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(slotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
    }

    @Test
    void bookingAnAlreadyBookedSlotIsRejectedAsAlreadyBooked() {
        Doctor doctor = doctor();
        Slot slot = slot(doctor, SlotStatus.BOOKED, LocalDate.now().plusDays(2));
        stubBookingContext(doctor, slot, UUID.randomUUID());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Check-up")))
                .isInstanceOf(SlotAlreadyBookedException.class);

        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void bookingAHeldSlotIsRejectedAsAlreadyBooked() {
        Doctor doctor = doctor();
        Slot slot = slot(doctor, SlotStatus.HELD, LocalDate.now().plusDays(2));
        stubBookingContext(doctor, slot, UUID.randomUUID());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Check-up")))
                .isInstanceOf(SlotAlreadyBookedException.class);
    }

    @Test
    void bookingABlockedSlotIsRejectedAsNotFound() {
        // BLOCKED is the clinic withholding the slot; to a patient it simply is
        // not a bookable slot, and the contract has no code for "blocked".
        Doctor doctor = doctor();
        Slot slot = slot(doctor, SlotStatus.BLOCKED, LocalDate.now().plusDays(2));
        stubBookingContext(doctor, slot, UUID.randomUUID());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Check-up")))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void bookingAnExpiredSlotIsRejectedAsNotFound() {
        Doctor doctor = doctor();
        Slot slot = slot(doctor, SlotStatus.EXPIRED, LocalDate.now().plusDays(2));
        stubBookingContext(doctor, slot, UUID.randomUUID());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Check-up")))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void bookingASlotInThePastIsRejectedAsNotFound() {
        Doctor doctor = doctor();
        Slot slot = slot(doctor, SlotStatus.AVAILABLE, LocalDate.now().minusDays(1));
        stubBookingContext(doctor, slot, UUID.randomUUID());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(doctor.getId(), slot.getId(), "Check-up")))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void aSlotBelongingToAnotherDoctorIsAFieldError() {
        Doctor requestedDoctor = doctor();
        Slot slotOfSomeoneElse = slot(doctor(), SlotStatus.AVAILABLE, LocalDate.now().plusDays(2));
        stubBookingContext(requestedDoctor, slotOfSomeoneElse, UUID.randomUUID());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(requestedDoctor.getId(), slotOfSomeoneElse.getId(), "Check-up")))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void bookingWithAnUnknownDoctorIs404() {
        when(patientService.requireOwnPatient()).thenReturn(patient(UUID.randomUUID()));
        when(doctorRepository.findById(any())).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(UUID.randomUUID(), UUID.randomUUID(), "Check-up")))
                .isInstanceOf(DoctorNotFoundException.class);
    }

    @Test
    void bookingAnUnknownSlotIs404() {
        Doctor doctor = doctor();
        when(patientService.requireOwnPatient()).thenReturn(patient(UUID.randomUUID()));
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(slotRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(doctor.getId(), UUID.randomUUID(), "Check-up")))
                .isInstanceOf(SlotNotFoundException.class);
    }

    private Appointment appointmentOf(UUID patientUserId, AppointmentStatus status) {
        Doctor doctor = doctor();
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient(patientUserId));
        appointment.setDoctor(doctor);
        appointment.setSlot(slot(doctor, SlotStatus.BOOKED, LocalDate.now().plusDays(2)));
        appointment.setStatus(status);
        appointment.setPaymentStatus(PaymentStatus.PENDING);
        return appointment;
    }

    @Test
    void aPatientCannotCancelAnotherPatientsAppointment() {
        Appointment appointment = appointmentOf(UUID.randomUUID(), AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.cancel(appointment.getId(),
                new CancelAppointmentRequest("Personal reason")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void anAlreadyCancelledAppointmentCannotBeCancelledAgain() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointmentOf(patientUserId, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(patientUserId, Role.PATIENT);

        assertThatThrownBy(() -> service.cancel(appointment.getId(),
                new CancelAppointmentRequest("Changed my mind")))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void reschedulingIntoTheSameSlotIsAFieldError() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointmentOf(patientUserId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(patientUserId, Role.PATIENT);

        assertThatThrownBy(() -> service.reschedule(appointment.getId(),
                new RescheduleAppointmentRequest(appointment.getSlot().getId())))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void aCompletedAppointmentCannotBeRescheduled() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointmentOf(patientUserId, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(patientUserId, Role.PATIENT);

        assertThatThrownBy(() -> service.reschedule(appointment.getId(),
                new RescheduleAppointmentRequest(UUID.randomUUID())))
                .isInstanceOf(FieldValidationException.class);
    }
}

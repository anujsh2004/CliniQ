package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ownership rules for appointments (API contract 19): a patient reaches only
 * their own appointments, and only the treating doctor may close one out.
 */
class AppointmentQueryServiceTest {

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final PatientService patientService = mock(PatientService.class);

    private final AppointmentQueryService service = new AppointmentQueryService(
            appointmentRepository, doctorRepository, patientService, new AppointmentMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "caller@example.com", role), null, List.of()));
    }

    private User user(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setPhone("+919876543210");
        return user;
    }

    private Appointment appointment(UUID patientUserId, UUID doctorUserId, AppointmentStatus status) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user(patientUserId, "Anjali Verma"));

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Sharma");
        doctor.setUser(user(doctorUserId, "Dr. Sharma"));

        Slot slot = new Slot();
        slot.setId(UUID.randomUUID());
        slot.setDate(LocalDate.of(2026, 8, 20));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setStatus(status);
        appointment.setPaymentStatus(PaymentStatus.PENDING);
        return appointment;
    }

    @Test
    void aPatientReadsTheirOwnAppointment() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointment(patientUserId, UUID.randomUUID(), AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(patientUserId, Role.PATIENT);

        var detail = service.get(appointment.getId());

        assertThat(detail.doctor().name()).isEqualTo("Dr. Sharma");
        assertThat(detail.patient().name()).isEqualTo("Anjali Verma");
        // The detail view's patient block carries no phone number.
        assertThat(detail.patient().phone()).isNull();
    }

    @Test
    void aPatientCannotReadAnotherPatientsAppointment() {
        Appointment appointment = appointment(UUID.randomUUID(), UUID.randomUUID(), AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.get(appointment.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);
    }

    @Test
    void theTreatingDoctorReadsTheAppointment() {
        UUID doctorUserId = UUID.randomUUID();
        Appointment appointment = appointment(UUID.randomUUID(), doctorUserId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(doctorUserId, Role.DOCTOR);

        assertThat(service.get(appointment.getId())).isNotNull();
    }

    @Test
    void anotherDoctorCannotReadTheAppointment() {
        Appointment appointment = appointment(UUID.randomUUID(), UUID.randomUUID(), AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(UUID.randomUUID(), Role.DOCTOR);

        assertThatThrownBy(() -> service.get(appointment.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void anUnknownAppointmentIs404() {
        when(appointmentRepository.findWithDetailsById(any())).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void theTreatingDoctorCompletesTheAppointmentAndTheFollowUpBecomesEligible() {
        UUID doctorUserId = UUID.randomUUID();
        Appointment appointment = appointment(UUID.randomUUID(), doctorUserId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(doctorUserId, Role.DOCTOR);

        var response = service.complete(appointment.getId());

        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(response.followUpEligible()).isTrue();
    }

    @Test
    void anotherDoctorCannotCompleteTheAppointment() {
        Appointment appointment = appointment(UUID.randomUUID(), UUID.randomUUID(), AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(UUID.randomUUID(), Role.DOCTOR);

        assertThatThrownBy(() -> service.complete(appointment.getId()))
                .isInstanceOf(ApiException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void aCancelledAppointmentCannotBeCompleted() {
        UUID doctorUserId = UUID.randomUUID();
        Appointment appointment = appointment(UUID.randomUUID(), doctorUserId, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        authenticateAs(doctorUserId, Role.DOCTOR);

        assertThatThrownBy(() -> service.complete(appointment.getId()))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void aDoctorsDailyListCarriesThePatientPhoneNumber() {
        UUID doctorUserId = UUID.randomUUID();
        Appointment appointment = appointment(UUID.randomUUID(), doctorUserId, AppointmentStatus.CONFIRMED);
        Doctor doctor = appointment.getDoctor();
        when(doctorRepository.findByUserId(doctorUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForDoctorOnDate(doctor.getId(), LocalDate.of(2026, 8, 20)))
                .thenReturn(List.of(appointment));
        authenticateAs(doctorUserId, Role.DOCTOR);

        var response = service.listOwnDay(LocalDate.of(2026, 8, 20));

        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(response.appointments()).hasSize(1);
        assertThat(response.appointments().getFirst().patient().phone()).isEqualTo("+919876543210");
    }

    @Test
    void anAccountWithNoDoctorProfileCannotOpenADailyList() {
        when(doctorRepository.findByUserId(any())).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), Role.DOCTOR);

        assertThatThrownBy(() -> service.listOwnDay(LocalDate.now()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);
    }
}

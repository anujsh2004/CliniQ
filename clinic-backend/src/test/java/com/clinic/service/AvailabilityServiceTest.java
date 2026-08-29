package com.clinic.service;

import com.clinic.dto.request.CreateAvailabilityRequest;
import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.repository.DoctorAvailabilityRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
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
 * Availability rules (API contract 11): who may define a window, and which
 * windows are coherent enough to generate slots from.
 */
class AvailabilityServiceTest {

    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final DoctorAvailabilityRepository availabilityRepository = mock(DoctorAvailabilityRepository.class);
    private final SlotRepository slotRepository = mock(SlotRepository.class);
    private final SlotGenerationService slotGenerationService = mock(SlotGenerationService.class);

    private final AvailabilityService service = new AvailabilityService(
            doctorRepository, availabilityRepository, slotRepository, slotGenerationService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "caller@example.com", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Doctor doctorOwnedBy(UUID userId) {
        User user = new User();
        user.setId(userId);
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        return doctor;
    }

    private CreateAvailabilityRequest request(LocalTime start, LocalTime end, int minutes) {
        return new CreateAvailabilityRequest(DayOfWeek.MONDAY, start, end, minutes);
    }

    private void stubSave() {
        when(availabilityRepository.saveAndFlush(any(DoctorAvailability.class)))
                .thenAnswer(invocation -> {
                    DoctorAvailability saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
    }

    @Test
    void aDoctorDefinesAvailabilityAndSlotsAreGeneratedImmediately() {
        UUID userId = UUID.randomUUID();
        Doctor doctor = doctorOwnedBy(userId);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(any(), any())).thenReturn(List.of());
        stubSave();
        authenticateAs(userId, Role.DOCTOR);

        var response = service.create(doctor.getId(), request(LocalTime.of(9, 0), LocalTime.of(17, 0), 30));

        assertThat(response.slotDurationMinutes()).isEqualTo(30);
        // Slots are materialised on the spot, so the doctor is bookable now
        // rather than after the next nightly run.
        verify(slotGenerationService).generateFor(any(DoctorAvailability.class));
    }

    @Test
    void aDoctorCannotDefineAvailabilityOnAnotherDoctorsProfile() {
        Doctor someoneElse = doctorOwnedBy(UUID.randomUUID());
        when(doctorRepository.findById(someoneElse.getId())).thenReturn(Optional.of(someoneElse));
        authenticateAs(UUID.randomUUID(), Role.DOCTOR);

        assertThatThrownBy(() -> service.create(someoneElse.getId(),
                request(LocalTime.of(9, 0), LocalTime.of(17, 0), 30)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);

        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void anAdminCanDefineAvailabilityOnBehalfOfADoctor() {
        Doctor doctor = doctorOwnedBy(UUID.randomUUID());
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(any(), any())).thenReturn(List.of());
        stubSave();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThat(service.create(doctor.getId(), request(LocalTime.of(9, 0), LocalTime.of(17, 0), 30)))
                .isNotNull();
    }

    @Test
    void endTimeMustBeAfterStartTime() {
        Doctor doctor = doctorOwnedBy(UUID.randomUUID());
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(doctor.getId(),
                request(LocalTime.of(17, 0), LocalTime.of(9, 0), 30)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void aSlotLongerThanTheWindowIsRejected() {
        Doctor doctor = doctorOwnedBy(UUID.randomUUID());
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(doctor.getId(),
                request(LocalTime.of(9, 0), LocalTime.of(9, 20), 30)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void anOverlappingWindowOnTheSameDayIsRejected() {
        Doctor doctor = doctorOwnedBy(UUID.randomUUID());
        DoctorAvailability existing = new DoctorAvailability();
        existing.setStartTime(LocalTime.of(9, 0));
        existing.setEndTime(LocalTime.of(13, 0));
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(any(), any())).thenReturn(List.of(existing));
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(doctor.getId(),
                request(LocalTime.of(12, 0), LocalTime.of(17, 0), 30)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void anAdjacentWindowOnTheSameDayIsAllowed() {
        // 09:00-13:00 followed by 13:00-17:00 is a lunch-free split shift, not
        // an overlap.
        Doctor doctor = doctorOwnedBy(UUID.randomUUID());
        DoctorAvailability morning = new DoctorAvailability();
        morning.setStartTime(LocalTime.of(9, 0));
        morning.setEndTime(LocalTime.of(13, 0));
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(any(), any())).thenReturn(List.of(morning));
        stubSave();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThat(service.create(doctor.getId(), request(LocalTime.of(13, 0), LocalTime.of(17, 0), 30)))
                .isNotNull();
    }

    @Test
    void availabilityForAnUnknownDoctorIs404() {
        when(doctorRepository.findById(any())).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(),
                request(LocalTime.of(9, 0), LocalTime.of(17, 0), 30)))
                .isInstanceOf(DoctorNotFoundException.class);
    }

    @Test
    void slotsForAnUnknownDoctorIs404() {
        when(doctorRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.slotsFor(UUID.randomUUID(), LocalDate.now()))
                .isInstanceOf(DoctorNotFoundException.class);
    }
}

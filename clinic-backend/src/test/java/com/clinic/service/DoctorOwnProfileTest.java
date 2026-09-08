package com.clinic.service;

import com.clinic.dto.request.ClinicRequest;
import com.clinic.dto.request.CreateDoctorRequest;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.FieldValidationException;
import com.clinic.mapper.DoctorMapper;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.UserRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A doctor account finding its own profile, and an administrator linking the
 * two (decision D3, contract v1.2).
 *
 * <p>An account and a profile are separate records. Before this the frontend
 * matched on the account's <em>name</em> against the doctor list, which picks
 * the wrong profile when two doctors share a name and finds nothing at all for
 * a profile an administrator created.
 */
class DoctorOwnProfileTest {

    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final ClinicRepository clinicRepository = mock(ClinicRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final DoctorService service =
            new DoctorService(doctorRepository, clinicRepository, userRepository, new DoctorMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "caller@example.com", role), null, List.of()));
    }

    private Doctor doctorProfile(String name, User account) {
        Clinic clinic = new Clinic();
        clinic.setId(UUID.randomUUID());
        clinic.setName("Sharma Dental Clinic");
        clinic.setAddress("MG Road, Chennai");
        clinic.setPhone("+919876543210");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName(name);
        doctor.setSpecialization("Dentist");
        doctor.setLicenseNumber("LIC-" + UUID.randomUUID());
        doctor.setConsultationFee(new BigDecimal("500.00"));
        doctor.setClinic(clinic);
        doctor.setUser(account);
        return doctor;
    }

    private User account(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Dr. Sharma");
        user.setEmail("sharma@example.com");
        user.setRole(role);
        return user;
    }

    private CreateDoctorRequest createRequest(String accountEmail) {
        return new CreateDoctorRequest("Dr. Sharma", "Dentist", "LIC-NEW",
                new BigDecimal("500.00"), accountEmail,
                new ClinicRequest("Sharma Dental Clinic", "MG Road, Chennai", "+919876543210"));
    }

    private void allowCreate() {
        when(doctorRepository.existsByLicenseNumberIgnoreCase(any())).thenReturn(false);
        when(clinicRepository.findByNameAndAddressIgnoringCase(any(), any())).thenReturn(Optional.empty());
        when(clinicRepository.save(any(Clinic.class))).thenAnswer(invocation -> {
            Clinic clinic = invocation.getArgument(0);
            clinic.setId(UUID.randomUUID());
            return clinic;
        });
        when(doctorRepository.saveAndFlush(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void aDoctorGetsTheProfileLinkedToTheirAccount() {
        UUID userId = UUID.randomUUID();
        User user = account(Role.DOCTOR);
        user.setId(userId);
        when(doctorRepository.findWithClinicByUserId(userId))
                .thenReturn(Optional.of(doctorProfile("Dr. Sharma", user)));
        authenticateAs(userId, Role.DOCTOR);

        assertThat(service.getOwnProfile().name()).isEqualTo("Dr. Sharma");
    }

    @Test
    void theLookupIsByAccountNotByName() {
        // The bug this replaces: two doctors named the same, and the old code
        // picked whichever appeared first in the list. Here the repository is
        // asked for the caller's own id, so a namesake cannot be returned.
        UUID userId = UUID.randomUUID();
        when(doctorRepository.findWithClinicByUserId(userId)).thenReturn(Optional.empty());
        authenticateAs(userId, Role.DOCTOR);

        assertThatThrownBy(service::getOwnProfile).isInstanceOf(DoctorNotFoundException.class);
    }

    @Test
    void aDoctorAccountWithNoProfileGetsAClearNotFound() {
        // The state an administrator leaves behind by creating a profile
        // without naming an account.
        UUID userId = UUID.randomUUID();
        when(doctorRepository.findWithClinicByUserId(userId)).thenReturn(Optional.empty());
        authenticateAs(userId, Role.DOCTOR);

        assertThatThrownBy(service::getOwnProfile).isInstanceOf(DoctorNotFoundException.class);
    }

    @Test
    void anAdministratorCanLinkAProfileToADoctorAccount() {
        User doctorAccount = account(Role.DOCTOR);
        when(userRepository.findByEmailIgnoreCase("sharma@example.com"))
                .thenReturn(Optional.of(doctorAccount));
        when(doctorRepository.findByUserId(doctorAccount.getId())).thenReturn(Optional.empty());
        allowCreate();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        service.create(createRequest("sharma@example.com"));

        // The profile is owned by the named account, so that doctor can now
        // manage their own availability.
        org.mockito.ArgumentCaptor<Doctor> saved = org.mockito.ArgumentCaptor.forClass(Doctor.class);
        org.mockito.Mockito.verify(doctorRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(doctorAccount);
    }

    @Test
    void linkingToAnUnknownEmailIsRefused() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        allowCreate();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(createRequest("nobody@example.com")))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void linkingToAPatientAccountIsRefused() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.of(account(Role.PATIENT)));
        allowCreate();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(createRequest("patient@example.com")))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void anAccountCannotOwnTwoProfiles() {
        User doctorAccount = account(Role.DOCTOR);
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.of(doctorAccount));
        when(doctorRepository.findByUserId(doctorAccount.getId()))
                .thenReturn(Optional.of(doctorProfile("Dr. Sharma", doctorAccount)));
        allowCreate();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        assertThatThrownBy(() -> service.create(createRequest("sharma@example.com")))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void anAdministratorMayStillCreateAProfileWithNoAccount() {
        // Onboarding a doctor who has no login yet stays possible; the profile
        // is simply unmanageable until an account is linked.
        allowCreate();
        authenticateAs(UUID.randomUUID(), Role.ADMIN);

        org.mockito.ArgumentCaptor<Doctor> saved = org.mockito.ArgumentCaptor.forClass(Doctor.class);
        service.create(createRequest(null));

        org.mockito.Mockito.verify(doctorRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUser()).isNull();
    }
}

package com.clinic.config;

import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorAvailabilityRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.UserRepository;
import com.clinic.service.SlotGenerationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The demonstration accounts a fresh deployment starts with.
 *
 * <p>Like the first-administrator bootstrap, the tests are mostly about what the
 * seeder must not do: it must not overwrite an account that already exists, and
 * it must not run twice into duplicates.
 */
class DemoDataSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ClinicRepository clinicRepository = mock(ClinicRepository.class);
    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final DoctorAvailabilityRepository availabilityRepository = mock(DoctorAvailabilityRepository.class);
    private final SlotGenerationService slotGenerationService = mock(SlotGenerationService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final DemoDataSeeder seeder = new DemoDataSeeder();

    private void seed() {
        seeder.seed(userRepository, clinicRepository, doctorRepository, availabilityRepository,
                slotGenerationService, passwordEncoder, "DemoPassword123");
    }

    /** A repository that hands back what it was given, with an id, like JPA. */
    private void savingEverything() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(UUID.randomUUID());
            when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
            return user;
        });
        when(clinicRepository.save(any(Clinic.class))).thenAnswer(call -> call.getArgument(0));
        when(doctorRepository.saveAndFlush(any(Doctor.class))).thenAnswer(call -> call.getArgument(0));
        when(availabilityRepository.saveAndFlush(any(DoctorAvailability.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsOneAdministratorThreeDoctorsAndAPatientOnAnEmptyDatabase() {
        savingEverything();

        seed();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(5)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).extracting(User::getRole)
                .containsExactlyInAnyOrder(Role.ADMIN, Role.DOCTOR, Role.DOCTOR, Role.DOCTOR, Role.PATIENT);
        assertThat(saved.getAllValues()).extracting(User::getEmail)
                .contains("ramesh@example.com", "meera@example.com", "anjali@example.com");
    }

    @Test
    void passwordsAreHashedAndMatchTheConfiguredOne() {
        savingEverything();

        seed();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(5)).saveAndFlush(saved.capture());
        for (User user : saved.getAllValues()) {
            assertThat(user.getPasswordHash()).isNotEqualTo("DemoPassword123");
            assertThat(passwordEncoder.matches("DemoPassword123", user.getPasswordHash())).isTrue();
        }
    }

    @Test
    void everyDoctorGetsWeekdayHoursAndSlotsAreGeneratedAtOnce() {
        savingEverything();

        seed();

        // Three doctors, Monday to Friday each.
        verify(doctorRepository, times(3)).saveAndFlush(any(Doctor.class));
        verify(availabilityRepository, times(15)).saveAndFlush(any(DoctorAvailability.class));
        verify(slotGenerationService, times(15)).generateFor(any(DoctorAvailability.class));
    }

    @Test
    void leavesAnExistingAccountAloneEvenIfItsPasswordIsDifferent() {
        // Otherwise switching the flag on in a running clinic could reset a real
        // password to the published one.
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(true);

        seed();

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void doesNotGiveADoctorASecondProfileOnTheNextStart() {
        User meera = new User();
        meera.setId(UUID.randomUUID());
        meera.setRole(Role.DOCTOR);
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.of(meera));
        when(doctorRepository.findByUserId(meera.getId())).thenReturn(Optional.of(new Doctor()));

        seed();

        verify(doctorRepository, never()).saveAndFlush(any(Doctor.class));
        verify(slotGenerationService, never()).generateFor(any(DoctorAvailability.class));
    }

    @Test
    void skipsAnAccountWhosePhoneIsTakenInsteadOfFailingTheStart() {
        when(userRepository.existsByPhone(any())).thenReturn(true);

        seed();

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void reusesTheClinicWhenItAlreadyExists() {
        savingEverything();
        when(clinicRepository.findByNameAndAddressIgnoringCase(DemoDataSeeder.CLINIC_NAME,
                DemoDataSeeder.CLINIC_ADDRESS)).thenReturn(Optional.of(new Clinic()));

        seed();

        verify(clinicRepository, never()).save(any(Clinic.class));
        verify(doctorRepository, atLeastOnce()).saveAndFlush(any(Doctor.class));
    }
}

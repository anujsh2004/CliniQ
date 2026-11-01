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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Creates the demonstration accounts: one administrator, three doctors with
 * bookable weekly hours, and one patient.
 *
 * <p>Off unless {@code clinic.demo-data.enabled} is true. A fresh deployment
 * starts with an empty database, so without this a demo has nobody to sign in
 * as. The accounts share one published password, so this must never be turned
 * on for a clinic with real patients.
 *
 * <p>It is safe to run on every start. An account that already exists is left
 * exactly as it is, password included, and a doctor who already has working
 * hours keeps them.
 */
@Configuration
@ConditionalOnProperty(prefix = "clinic.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String CLINIC_NAME = "Cliniva Clinic";
    static final String CLINIC_ADDRESS = "12 MG Road, Bengaluru";
    static final String CLINIC_PHONE = "+919876543210";

    record Account(String name, String email, String phone, Role role) {
    }

    record DoctorProfile(String email, String specialization, String licenseNumber, BigDecimal fee) {
    }

    static final List<Account> ACCOUNTS = List.of(
            new Account("Ramesh Admin", "ramesh@example.com", "+919876500010", Role.ADMIN),
            new Account("Dr. Meera Sharma", "meera@example.com", "+919876500011", Role.DOCTOR),
            new Account("Dr. Arvind Rao", "arvind@example.com", "+919876500012", Role.DOCTOR),
            new Account("Dr. Kavya Iyer", "kavya@example.com", "+919876500013", Role.DOCTOR),
            new Account("Anjali Verma", "anjali@example.com", "+919876500099", Role.PATIENT));

    static final List<DoctorProfile> DOCTORS = List.of(
            new DoctorProfile("meera@example.com", "Dentist", "DEMO-DEN-001", new BigDecimal("500.00")),
            new DoctorProfile("arvind@example.com", "General Physician", "DEMO-GP-002", new BigDecimal("400.00")),
            new DoctorProfile("kavya@example.com", "Dermatologist", "DEMO-DER-003", new BigDecimal("650.00")));

    private static final List<DayOfWeek> WORKING_DAYS = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    @Bean
    public ApplicationRunner seedDemoData(UserRepository userRepository,
                                          ClinicRepository clinicRepository,
                                          DoctorRepository doctorRepository,
                                          DoctorAvailabilityRepository availabilityRepository,
                                          SlotGenerationService slotGenerationService,
                                          PasswordEncoder passwordEncoder,
                                          @Value("${clinic.demo-data.password:StrongPassword123}") String password) {
        return args -> seed(userRepository, clinicRepository, doctorRepository, availabilityRepository,
                slotGenerationService, passwordEncoder, password);
    }

    void seed(UserRepository userRepository,
              ClinicRepository clinicRepository,
              DoctorRepository doctorRepository,
              DoctorAvailabilityRepository availabilityRepository,
              SlotGenerationService slotGenerationService,
              PasswordEncoder passwordEncoder,
              String password) {
        log.warn("Demo data is enabled: the demonstration accounts share one published password. "
                + "Never enable this for a clinic with real patients.");

        for (Account account : ACCOUNTS) {
            ensureUser(userRepository, passwordEncoder, password, account);
        }

        Clinic clinic = null;
        for (DoctorProfile profile : DOCTORS) {
            User user = userRepository.findByEmailIgnoreCase(profile.email()).orElse(null);
            if (user == null || user.getRole() != Role.DOCTOR) {
                continue;
            }
            if (doctorRepository.findByUserId(user.getId()).isPresent()) {
                continue;
            }
            if (doctorRepository.existsByLicenseNumberIgnoreCase(profile.licenseNumber())) {
                log.warn("Demo doctor {} not created: licence {} is already in use",
                        profile.email(), profile.licenseNumber());
                continue;
            }
            if (clinic == null) {
                clinic = ensureClinic(clinicRepository);
            }

            Doctor doctor = new Doctor();
            doctor.setUser(user);
            doctor.setClinic(clinic);
            doctor.setName(user.getName());
            doctor.setSpecialization(profile.specialization());
            doctor.setLicenseNumber(profile.licenseNumber());
            doctor.setConsultationFee(profile.fee());
            Doctor saved = doctorRepository.saveAndFlush(doctor);

            for (DayOfWeek day : WORKING_DAYS) {
                DoctorAvailability availability = new DoctorAvailability();
                availability.setDoctor(saved);
                availability.setDayOfWeek(day);
                availability.setStartTime(LocalTime.of(9, 0));
                availability.setEndTime(LocalTime.of(17, 0));
                availability.setSlotDurationMinutes(30);
                // Slots are generated now so the doctor is bookable at once,
                // not after the nightly job.
                slotGenerationService.generateFor(availabilityRepository.saveAndFlush(availability));
            }
            log.info("Created demo doctor {} with weekly hours", user.getEmail());
        }
    }

    private void ensureUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            String password, Account account) {
        if (userRepository.existsByEmailIgnoreCase(account.email())) {
            return;
        }
        if (userRepository.existsByPhone(account.phone())) {
            log.warn("Demo account {} not created: phone {} is already registered",
                    account.email(), account.phone());
            return;
        }
        User user = new User();
        user.setName(account.name());
        user.setEmail(account.email());
        user.setPhone(account.phone());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(account.role());
        userRepository.saveAndFlush(user);
        log.info("Created demo {} account {}", account.role(), account.email());
    }

    private Clinic ensureClinic(ClinicRepository clinicRepository) {
        return clinicRepository.findByNameAndAddressIgnoringCase(CLINIC_NAME, CLINIC_ADDRESS)
                .orElseGet(() -> {
                    Clinic clinic = new Clinic();
                    clinic.setName(CLINIC_NAME);
                    clinic.setAddress(CLINIC_ADDRESS);
                    clinic.setPhone(CLINIC_PHONE);
                    return clinicRepository.save(clinic);
                });
    }
}

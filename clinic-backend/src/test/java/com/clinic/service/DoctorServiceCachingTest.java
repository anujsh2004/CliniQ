package com.clinic.service;

import com.clinic.config.CacheConfig;
import com.clinic.dto.request.ClinicRequest;
import com.clinic.dto.request.CreateDoctorRequest;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Role;
import com.clinic.mapper.DoctorMapper;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.UserRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caching behaviour of the doctor endpoints (tech-stack.md 3).
 *
 * <p>An in-memory cache manager stands in for Redis: what is being tested is
 * which methods are cached and when entries are evicted, which is a property of
 * the annotations rather than of the backing store. The real Redis wiring is
 * exercised by running the application.
 */
@SpringBootTest(classes = {
        DoctorService.class,
        DoctorMapper.class,
        DoctorServiceCachingTest.CachingTestConfig.class
})
class DoctorServiceCachingTest {

    @TestConfiguration
    @EnableCaching
    static class CachingTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.DOCTOR_LIST, CacheConfig.DOCTOR_DETAIL);
        }
    }

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private DoctorRepository doctorRepository;

    @MockitoBean
    private ClinicRepository clinicRepository;

    @MockitoBean
    private UserRepository userRepository;

    @AfterEach
    void clearCachesAndContext() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        SecurityContextHolder.clearContext();
    }

    private Doctor doctor() {
        Clinic clinic = new Clinic();
        clinic.setId(UUID.randomUUID());
        clinic.setName("Sharma Dental Clinic");
        clinic.setAddress("MG Road, Chennai");
        clinic.setPhone("+919876543210");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setClinic(clinic);
        doctor.setName("Dr. Sharma");
        doctor.setSpecialization("Dentist");
        doctor.setLicenseNumber("LIC-" + UUID.randomUUID());
        doctor.setConsultationFee(new BigDecimal("500.00"));
        return doctor;
    }

    @Test
    void aRepeatedDoctorLookupIsServedFromTheCache() {
        Doctor doctor = doctor();
        when(doctorRepository.findWithClinicById(doctor.getId())).thenReturn(Optional.of(doctor));

        doctorService.get(doctor.getId());
        doctorService.get(doctor.getId());

        // Second call never reached the database.
        verify(doctorRepository, times(1)).findWithClinicById(doctor.getId());
    }

    @Test
    void eachPageOfTheDoctorListIsCachedSeparately() {
        // A shared key would serve page 2 the contents of page 1.
        when(doctorRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor())));

        doctorService.list(PageRequest.of(0, 10));
        doctorService.list(PageRequest.of(1, 10));
        doctorService.list(PageRequest.of(0, 10));

        verify(doctorRepository, times(2)).findAllBy(any(Pageable.class));
        assertThat(cacheManager.getCache(CacheConfig.DOCTOR_LIST).get("0-10")).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.DOCTOR_LIST).get("1-10")).isNotNull();
    }

    @Test
    void addingADoctorEvictsTheCachedListAndProfiles() {
        // Otherwise a new doctor would be invisible to patients until the TTL
        // expired.
        Doctor existing = doctor();
        when(doctorRepository.findWithClinicById(existing.getId())).thenReturn(Optional.of(existing));
        when(doctorRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing)));
        doctorService.get(existing.getId());
        doctorService.list(PageRequest.of(0, 10));
        assertThat(cacheManager.getCache(CacheConfig.DOCTOR_LIST).get("0-10")).isNotNull();

        when(doctorRepository.existsByLicenseNumberIgnoreCase(any())).thenReturn(false);
        when(clinicRepository.findByNameAndAddressIgnoringCase(any(), any()))
                .thenReturn(Optional.of(existing.getClinic()));
        when(doctorRepository.saveAndFlush(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN), null, List.of()));

        doctorService.create(new CreateDoctorRequest("Dr. New", "General", "LIC-NEW",
                new BigDecimal("300.00"),
                new ClinicRequest("Sharma Dental Clinic", "MG Road, Chennai", "+919876543210")));

        assertThat(cacheManager.getCache(CacheConfig.DOCTOR_LIST).get("0-10")).isNull();
        assertThat(cacheManager.getCache(CacheConfig.DOCTOR_DETAIL).get(existing.getId())).isNull();
    }
}

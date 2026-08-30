package com.clinic.repository;

import com.clinic.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    Optional<Doctor> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "clinic")
    Optional<Doctor> findWithClinicById(UUID id);

    @EntityGraph(attributePaths = "clinic")
    Page<Doctor> findAllBy(Pageable pageable);
}

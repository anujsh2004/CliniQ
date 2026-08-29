package com.clinic.repository;

import com.clinic.entity.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    @Query("SELECT c FROM Clinic c WHERE LOWER(c.name) = LOWER(:name) AND LOWER(c.address) = LOWER(:address)")
    Optional<Clinic> findByNameAndAddressIgnoringCase(String name, String address);
}

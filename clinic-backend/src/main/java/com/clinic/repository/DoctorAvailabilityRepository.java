package com.clinic.repository;

import com.clinic.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, UUID> {

    List<DoctorAvailability> findByDoctorId(UUID doctorId);

    List<DoctorAvailability> findByDoctorIdAndDayOfWeek(UUID doctorId, DayOfWeek dayOfWeek);
}

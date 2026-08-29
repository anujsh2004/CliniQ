package com.clinic.repository;

import com.clinic.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @EntityGraph(attributePaths = {"doctor", "patient", "patient.user", "slot"})
    Optional<Appointment> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"doctor", "slot"})
    Page<Appointment> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "patient.user", "slot"})
    @Query("""
            SELECT a FROM Appointment a
             WHERE a.doctor.id = :doctorId
               AND a.slot.date = :date
             ORDER BY a.slot.startTime
            """)
    List<Appointment> findForDoctorOnDate(@Param("doctorId") UUID doctorId, @Param("date") LocalDate date);

    boolean existsBySlotIdAndStatusIn(UUID slotId, List<com.clinic.entity.AppointmentStatus> statuses);

    /** Appointments still live on a given date, for the reminder sweep. */
    @EntityGraph(attributePaths = {"doctor", "patient", "patient.user", "slot"})
    @Query("""
            SELECT a FROM Appointment a
             WHERE a.slot.date = :date
               AND a.status IN :statuses
            """)
    List<Appointment> findActiveOnDate(@Param("date") LocalDate date,
                                       @Param("statuses") List<com.clinic.entity.AppointmentStatus> statuses);
}

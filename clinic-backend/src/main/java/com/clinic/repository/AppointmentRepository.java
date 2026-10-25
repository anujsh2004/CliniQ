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

    /** Live appointments whose hold on a slot has run out (v1.4). */
    List<Appointment> findByStatusAndHoldExpiresAtBefore(
            com.clinic.entity.AppointmentStatus status, java.time.OffsetDateTime before);

    /**
     * Each patient's earlier visits with a known outcome, for no-show risk
     * (v1.6). Strictly before the date, so a same-day appointment's outcome
     * never feeds the score of another on that day.
     */
    @Query("""
            SELECT a.patient.id AS patientId,
                   COUNT(a) AS visits,
                   SUM(CASE WHEN a.status = com.clinic.entity.AppointmentStatus.NO_SHOW THEN 1 ELSE 0 END) AS noShows
              FROM Appointment a
             WHERE a.patient.id IN :patientIds
               AND a.slot.date < :before
               AND a.status IN :outcomes
             GROUP BY a.patient.id
            """)
    List<PatientAttendance> attendanceBefore(@Param("patientIds") java.util.Collection<UUID> patientIds,
                                             @Param("before") LocalDate before,
                                             @Param("outcomes") List<com.clinic.entity.AppointmentStatus> outcomes);

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

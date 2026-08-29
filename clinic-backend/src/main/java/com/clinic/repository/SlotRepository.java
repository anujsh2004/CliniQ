package com.clinic.repository;

import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    List<Slot> findByDoctorIdAndDateOrderByStartTime(UUID doctorId, LocalDate date);

    List<Slot> findByDoctorIdAndDateBetween(UUID doctorId, LocalDate from, LocalDate to);

    boolean existsByDoctorIdAndDateAndStartTime(UUID doctorId, LocalDate date, LocalTime startTime);

    /**
     * Marks slots that are still AVAILABLE but whose time has passed as EXPIRED,
     * so a stale slot can never be booked.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Slot s
               SET s.status = com.clinic.entity.SlotStatus.EXPIRED
             WHERE s.status = com.clinic.entity.SlotStatus.AVAILABLE
               AND (s.date < :today OR (s.date = :today AND s.startTime <= :now))
            """)
    int expirePastAvailableSlots(@Param("today") LocalDate today, @Param("now") LocalTime now);

    long countByDoctorIdAndStatus(UUID doctorId, SlotStatus status);
}

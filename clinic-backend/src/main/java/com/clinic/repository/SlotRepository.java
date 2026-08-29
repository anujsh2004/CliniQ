package com.clinic.repository;

import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    List<Slot> findByDoctorIdAndDateOrderByStartTime(UUID doctorId, LocalDate date);

    /**
     * Takes a row-level write lock on the slot (SELECT ... FOR UPDATE) for the
     * duration of the surrounding transaction.
     *
     * <p>This is the single most important query in the product. Two concurrent
     * bookings for one slot serialise here: the first transaction holds the
     * lock while it checks the status and books the slot, and the second only
     * sees the row after that commit - by which time the slot is BOOKED and the
     * booking is rejected with SLOT_ALREADY_BOOKED. Reading the slot without
     * this lock would let both callers see AVAILABLE and both proceed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :slotId")
    Optional<Slot> findByIdForUpdate(@Param("slotId") UUID slotId);

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

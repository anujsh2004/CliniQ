package com.clinic.repository;

import com.clinic.entity.Notification;
import com.clinic.entity.NotificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByAppointmentIdAndReminderType(UUID appointmentId, String reminderType);

    List<Notification> findByStatus(NotificationStatus status);

    /** Reminders whose time has come and which have not been delivered yet. */
    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.slot"})
    List<Notification> findByStatusAndScheduledForBefore(
            NotificationStatus status, java.time.OffsetDateTime before);

    /**
     * What a patient sees: their own reminders that are actually due, newest
     * first. A reminder scheduled for tomorrow is not shown today.
     */
    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.slot"})
    @Query("""
            SELECT n FROM Notification n
             WHERE n.appointment.patient.user.id = :userId
               AND n.status <> com.clinic.entity.NotificationStatus.QUEUED
             ORDER BY n.scheduledFor DESC
            """)
    List<Notification> findDeliveredForUser(@Param("userId") UUID userId);

    long countByAppointmentPatientUserIdAndReadAtIsNullAndStatusNot(
            UUID userId, NotificationStatus excluded);
}

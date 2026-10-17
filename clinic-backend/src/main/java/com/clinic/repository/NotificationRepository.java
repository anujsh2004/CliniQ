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
     * Every reminder belonging to a patient, delivered or still scheduled.
     *
     * <p>Scheduled ones are included deliberately: a patient who has just
     * booked has reminders coming but none delivered, and an empty page would
     * suggest the clinic had forgotten them. The client groups the two.
     */
    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.slot"})
    @Query("""
            SELECT n FROM Notification n
             WHERE n.appointment.patient.user.id = :userId
             ORDER BY n.scheduledFor DESC
            """)
    List<Notification> findDeliveredForUser(@Param("userId") UUID userId);

    long countByAppointmentPatientUserIdAndReadAtIsNullAndStatusNot(
            UUID userId, NotificationStatus excluded);
}

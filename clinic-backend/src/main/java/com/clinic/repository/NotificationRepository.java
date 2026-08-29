package com.clinic.repository;

import com.clinic.entity.Notification;
import com.clinic.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByAppointmentIdAndReminderType(UUID appointmentId, String reminderType);

    List<Notification> findByStatus(NotificationStatus status);
}

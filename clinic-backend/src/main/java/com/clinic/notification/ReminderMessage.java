package com.clinic.notification;

import com.clinic.entity.NotificationChannel;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * What travels on the queue.
 *
 * <p>Carries everything the worker needs to compose and send the message, so
 * delivery never has to read the database. That keeps the worker independent of
 * the API's schema and lets it be split into its own process later without
 * changing the contract between them.
 */
public record ReminderMessage(
        UUID notificationId,
        UUID appointmentId,
        UUID patientId,
        String patientName,
        String patientPhone,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        NotificationChannel channel,
        String reminderType) implements Serializable {
}

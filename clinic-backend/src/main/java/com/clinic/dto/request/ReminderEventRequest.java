package com.clinic.dto.request;

import com.clinic.entity.NotificationChannel;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * POST /api/v1/internal/notifications/reminders (API contract 15).
 *
 * <p>Only the appointment is required; channel, reminder type and schedule fall
 * back to the contract's defaults of WhatsApp, 24_HOURS, and 24 hours before
 * the appointment.
 */
public record ReminderEventRequest(

        @NotNull(message = "Appointment id is required")
        UUID appointmentId,

        UUID patientId,

        NotificationChannel channel,

        String reminderType,

        OffsetDateTime scheduledFor) {
}

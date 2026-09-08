package com.clinic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * POST /api/v1/notifications/reminders (API contract 15, v1.4).
 *
 * <p>A reminder the patient chooses, on top of the clinic's defaults.
 */
public record PatientReminderRequest(

        @NotNull(message = "Appointment id is required")
        UUID appointmentId,

        @NotNull(message = "Choose how long before the appointment to be reminded")
        @Min(value = 5, message = "Choose at least 5 minutes before")
        // A week: beyond that the reminder is likelier to be noise than help.
        @Max(value = 10080, message = "Choose at most a week before")
        Integer minutesBefore) {
}

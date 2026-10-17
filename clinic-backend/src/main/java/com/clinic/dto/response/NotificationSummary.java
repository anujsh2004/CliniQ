package com.clinic.dto.response;

import com.clinic.entity.NotificationStatus;

import java.time.OffsetDateTime;

/**
 * One row of the patient's reminder list (API contract 15, v1.4).
 *
 * <p>{@code patientRequested} tells the client which reminders the patient may
 * remove: the clinic's defaults are not theirs to delete.
 */
public record NotificationSummary(
        String notificationId,
        String appointmentId,
        String message,
        String reminderType,
        OffsetDateTime scheduledFor,
        NotificationStatus status,
        boolean read,
        boolean patientRequested) {
}

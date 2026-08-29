package com.clinic.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * POST /api/v1/doctors/{doctorId}/availability (API contract 11).
 *
 * <p>The ordering rule (start before end) and the requirement that at least one
 * whole slot fits are checked in the service, since they span fields.
 */
public record CreateAvailabilityRequest(

        @NotNull(message = "Day of week is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Start time is required")
        @JsonFormat(pattern = "HH:mm[:ss]")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        @JsonFormat(pattern = "HH:mm[:ss]")
        LocalTime endTime,

        @Min(value = 5, message = "Slot duration must be at least 5 minutes")
        @Max(value = 480, message = "Slot duration must be at most 480 minutes")
        int slotDurationMinutes) {
}

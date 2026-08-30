package com.clinic.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Availability creation result (API contract 11).
 */
public record AvailabilityResponse(
        String availabilityId,
        String doctorId,
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        int slotDurationMinutes) {
}

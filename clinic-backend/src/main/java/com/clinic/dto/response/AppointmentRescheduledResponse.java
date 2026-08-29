package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * PATCH /api/v1/appointments/{appointmentId}/reschedule (API contract 12).
 */
public record AppointmentRescheduledResponse(
        String appointmentId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        AppointmentStatus status) {
}

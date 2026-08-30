package com.clinic.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/doctors/me/appointments?date=... (API contract 13). As with the
 * slot response, the date lives once at the top level.
 */
public record DoctorDayAppointmentsResponse(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        List<DoctorDayAppointment> appointments) {
}

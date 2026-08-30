package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * GET /api/v1/appointments/{appointmentId} (API contract 12).
 */
public record AppointmentDetailResponse(
        String appointmentId,
        DoctorParty doctor,
        PatientParty patient,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        AppointmentStatus status,
        PaymentStatus paymentStatus) {
}

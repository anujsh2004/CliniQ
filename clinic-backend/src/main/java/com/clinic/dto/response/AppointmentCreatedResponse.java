package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Appointment creation result (API contract 12).
 */
public record AppointmentCreatedResponse(
        String appointmentId,
        String doctorId,
        String patientId,
        String slotId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate appointmentDate,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        AppointmentStatus status,
        PaymentStatus paymentStatus) {
}

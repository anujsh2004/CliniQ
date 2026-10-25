package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * One appointment in a doctor's daily list (API contract 13), including the
 * patient's phone so the front desk can call them.
 *
 * <p>{@code noShowRisk} is added in v1.6 and omitted when there is none: the ML
 * service is off or failing, or the appointment already has an outcome.
 */
public record DoctorDayAppointment(
        String appointmentId,
        PatientParty patient,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        AppointmentStatus status,
        NoShowRisk noShowRisk) {
}

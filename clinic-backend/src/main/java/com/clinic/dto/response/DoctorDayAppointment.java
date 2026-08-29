package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * One appointment in a doctor's daily list (API contract 13), including the
 * patient's phone so the front desk can call them.
 */
public record DoctorDayAppointment(
        String appointmentId,
        PatientParty patient,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        AppointmentStatus status) {
}

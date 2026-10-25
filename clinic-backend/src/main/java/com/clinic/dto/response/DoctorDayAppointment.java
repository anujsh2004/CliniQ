package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

/**
 * One appointment in a doctor's daily list (API contract 13), including the
 * patient's phone so the front desk can call them.
 *
 * <p>Added in v1.6, and each omitted when there is none:
 * {@code noShowRisk} (API contract 26); {@code symptoms}, the codes the patient
 * reported when booking, and {@code conditionHints}, what the model makes of
 * them (API contract 27).
 */
public record DoctorDayAppointment(
        String appointmentId,
        PatientParty patient,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        AppointmentStatus status,
        NoShowRisk noShowRisk,
        List<String> symptoms,
        ConditionHints conditionHints) {
}

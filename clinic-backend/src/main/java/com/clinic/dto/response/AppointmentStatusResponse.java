package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;

/**
 * The compact status payload the contract returns from cancel and complete
 * (API contract 12/13). {@code followUpEligible} is present only on completion
 * and suppressed otherwise.
 */
public record AppointmentStatusResponse(
        String appointmentId,
        AppointmentStatus status,
        Boolean followUpEligible) {

    public static AppointmentStatusResponse of(String appointmentId, AppointmentStatus status) {
        return new AppointmentStatusResponse(appointmentId, status, null);
    }
}

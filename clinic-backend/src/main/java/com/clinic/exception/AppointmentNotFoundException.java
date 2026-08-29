package com.clinic.exception;

/**
 * Appointment does not exist (API contract 7a).
 */
public class AppointmentNotFoundException extends ApiException {

    public AppointmentNotFoundException() {
        super(ErrorCode.APPOINTMENT_NOT_FOUND);
    }
}

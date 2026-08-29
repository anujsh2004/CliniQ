package com.clinic.exception;

import org.springframework.http.HttpStatus;

/**
 * The canonical error codes from API contract 7a. These values are the only
 * ones any layer may return; never hardcode error strings elsewhere.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"),
    SLOT_ALREADY_BOOKED(HttpStatus.CONFLICT, "Appointment slot is already booked"),
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Slot not found"),
    APPOINTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Appointment not found"),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "You are not allowed to access this resource"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    DOCTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "Doctor not found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "An account with this email already exists");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

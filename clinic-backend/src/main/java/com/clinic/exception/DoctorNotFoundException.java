package com.clinic.exception;

/**
 * Doctor does not exist (API contract 7a).
 */
public class DoctorNotFoundException extends ApiException {

    public DoctorNotFoundException() {
        super(ErrorCode.DOCTOR_NOT_FOUND);
    }
}

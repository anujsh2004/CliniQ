package com.clinic.exception;

import com.clinic.dto.response.FieldErrorDetail;

import java.util.List;

/**
 * A validation failure that only the service layer can detect (for example a
 * phone number already in use). Reported as VALIDATION_ERROR with the same
 * field-level {@code errors[]} shape as bean-validation failures, so clients
 * have one way to map errors onto form fields.
 */
public class FieldValidationException extends ApiException {

    private final transient List<FieldErrorDetail> fieldErrors;

    public FieldValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR);
        this.fieldErrors = List.of(new FieldErrorDetail(field, message));
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}

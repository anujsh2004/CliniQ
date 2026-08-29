package com.clinic.dto.response;

import com.clinic.config.RequestIdFilter;
import com.clinic.exception.ErrorCode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The standard error envelope from API contract 7. {@code errors} carries
 * field-level detail for VALIDATION_ERROR and is an empty list otherwise.
 */
public record ErrorResponse(
        boolean success,
        String message,
        ErrorCode errorCode,
        List<FieldErrorDetail> errors,
        OffsetDateTime timestamp,
        String requestId) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<FieldErrorDetail> errors) {
        return new ErrorResponse(false, message, errorCode, errors, OffsetDateTime.now(),
                RequestIdFilter.currentRequestId());
    }
}

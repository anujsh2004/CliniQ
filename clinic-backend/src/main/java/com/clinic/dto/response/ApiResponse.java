package com.clinic.dto.response;

import com.clinic.config.RequestIdFilter;

import java.time.OffsetDateTime;

/**
 * The standard success envelope from API contract 7. Every successful response
 * in the product is wrapped in this shape so the frontend needs no
 * feature-specific parsing.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        OffsetDateTime timestamp,
        String requestId) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, OffsetDateTime.now(), RequestIdFilter.currentRequestId());
    }

    public static ApiResponse<Void> success(String message) {
        return success(message, null);
    }
}

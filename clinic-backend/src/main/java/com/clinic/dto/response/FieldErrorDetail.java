package com.clinic.dto.response;

/**
 * One entry of the {@code errors[]} array in a VALIDATION_ERROR response
 * (API contract 7).
 */
public record FieldErrorDetail(String field, String message) {
}

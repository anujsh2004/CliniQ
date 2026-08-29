package com.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/v1/patients/me (API contract 10). Email is not updatable here: it is
 * the login identifier, and changing it is an auth concern the contract does
 * not define.
 */
public record UpdatePatientRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number is invalid")
        String phone) {
}

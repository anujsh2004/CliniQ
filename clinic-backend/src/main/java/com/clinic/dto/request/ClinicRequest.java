package com.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The clinic block embedded in a doctor creation request (API contract 9).
 */
public record ClinicRequest(

        @NotBlank(message = "Clinic name is required")
        @Size(max = 150, message = "Clinic name must be at most 150 characters")
        String name,

        @NotBlank(message = "Clinic address is required")
        @Size(max = 255, message = "Clinic address must be at most 255 characters")
        String address,

        @NotBlank(message = "Clinic phone number is required")
        @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number is invalid")
        String phone) {
}

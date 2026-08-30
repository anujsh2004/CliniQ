package com.clinic.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * POST /api/v1/doctors (API contract 9).
 */
public record CreateDoctorRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Specialization is required")
        @Size(max = 100, message = "Specialization must be at most 100 characters")
        String specialization,

        @NotBlank(message = "License number is required")
        @Size(max = 50, message = "License number must be at most 50 characters")
        String licenseNumber,

        @NotNull(message = "Consultation fee is required")
        @DecimalMin(value = "0.0", message = "Consultation fee cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Consultation fee has too many digits")
        BigDecimal consultationFee,

        @NotNull(message = "Clinic details are required")
        @Valid
        ClinicRequest clinic) {
}

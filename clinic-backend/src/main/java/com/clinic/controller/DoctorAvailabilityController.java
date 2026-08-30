package com.clinic.controller;

import com.clinic.dto.request.CreateAvailabilityRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.AvailabilityResponse;
import com.clinic.dto.response.DoctorSlotsResponse;
import com.clinic.service.AvailabilityService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Availability and slot endpoints (API contract 11). Defining availability is
 * restricted to the doctor themselves or an admin; reading slots is open to any
 * authenticated caller, since patients browse them to book.
 */
@RestController
@RequestMapping("/api/v1/doctors/{doctorId}")
public class DoctorAvailabilityController {

    private final AvailabilityService availabilityService;

    public DoctorAvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping("/availability")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> createAvailability(
            @PathVariable UUID doctorId,
            @Valid @RequestBody CreateAvailabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Availability created successfully", availabilityService.create(doctorId, request)));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<DoctorSlotsResponse>> slots(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("Available slots fetched successfully",
                availabilityService.slotsFor(doctorId, date)));
    }
}

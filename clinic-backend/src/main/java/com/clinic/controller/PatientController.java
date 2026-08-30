package com.clinic.controller;

import com.clinic.dto.request.UpdatePatientRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.PatientProfileResponse;
import com.clinic.dto.response.PatientUpdateResponse;
import com.clinic.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient APIs (API contract 10). Only /me exists by design: there is no
 * endpoint through which one patient could address another patient's profile.
 */
@RestController
@RequestMapping("/api/v1/patients")
@PreAuthorize("hasRole('PATIENT')")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getOwnProfile() {
        return ResponseEntity.ok(ApiResponse.success("Patient profile fetched successfully",
                patientService.getOwnProfile()));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<PatientUpdateResponse>> updateOwnProfile(
            @Valid @RequestBody UpdatePatientRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Patient profile updated successfully",
                patientService.updateOwnProfile(request)));
    }
}

package com.clinic.controller;

import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.AppointmentDetailResponse;
import com.clinic.dto.response.AppointmentListItem;
import com.clinic.dto.response.AppointmentStatusResponse;
import com.clinic.dto.response.PagedResponse;
import com.clinic.service.AppointmentQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Appointment read endpoints and completion (API contract 12 and 13).
 * Booking, cancellation and rescheduling live on their own controller, since
 * they change slot state.
 */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentQueryService appointmentQueryService;

    public AppointmentController(AppointmentQueryService appointmentQueryService) {
        this.appointmentQueryService = appointmentQueryService;
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentListItem>>> listOwn(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Appointments fetched successfully",
                appointmentQueryService.listOwn(pageable)));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<ApiResponse<AppointmentDetailResponse>> get(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success("Appointment fetched successfully",
                appointmentQueryService.get(appointmentId)));
    }

    @PatchMapping("/{appointmentId}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentStatusResponse>> complete(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success("Appointment marked as completed",
                appointmentQueryService.complete(appointmentId)));
    }
}

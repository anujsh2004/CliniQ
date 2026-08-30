package com.clinic.controller;

import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.DoctorDayAppointmentsResponse;
import com.clinic.service.AppointmentQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * A doctor's own daily schedule (API contract 13).
 */
@RestController
@RequestMapping("/api/v1/doctors/me")
public class DoctorScheduleController {

    private final AppointmentQueryService appointmentQueryService;

    public DoctorScheduleController(AppointmentQueryService appointmentQueryService) {
        this.appointmentQueryService = appointmentQueryService;
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<DoctorDayAppointmentsResponse>> ownDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("Doctor appointments fetched successfully",
                appointmentQueryService.listOwnDay(date)));
    }
}

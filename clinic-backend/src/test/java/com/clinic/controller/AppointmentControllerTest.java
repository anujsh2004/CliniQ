package com.clinic.controller;

import com.clinic.dto.response.AppointmentDetailResponse;
import com.clinic.dto.response.AppointmentListItem;
import com.clinic.dto.response.AppointmentStatusResponse;
import com.clinic.dto.response.DoctorParty;
import com.clinic.dto.response.PagedResponse;
import com.clinic.dto.response.PatientParty;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.PaymentStatus;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.security.JwtService;
import com.clinic.service.AppointmentBookingService;
import com.clinic.service.AppointmentQueryService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Appointment read endpoints against the payload shapes in API contract 12.
 */
@WebMvcTest(AppointmentController.class)
@Import(SecuritySliceTestConfig.class)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private AppointmentBookingService appointmentBookingService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "PATIENT")
    void theDetailViewUsesTheContractsDoctorAndPatientBlocks() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentQueryService.get(appointmentId)).thenReturn(new AppointmentDetailResponse(
                appointmentId.toString(),
                new DoctorParty(UUID.randomUUID().toString(), "Dr. Sharma"),
                PatientParty.withoutPhone(UUID.randomUUID().toString(), "Anuj Kumar"),
                LocalDate.of(2026, 8, 20), LocalTime.of(10, 0), LocalTime.of(10, 30),
                AppointmentStatus.CONFIRMED, PaymentStatus.PAID));

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment fetched successfully"))
                // The contract spells these doctorId and patientId, not id.
                .andExpect(jsonPath("$.data.doctor.doctorId").exists())
                .andExpect(jsonPath("$.data.patient.patientId").exists())
                .andExpect(jsonPath("$.data.date").value("2026-08-20"))
                .andExpect(jsonPath("$.data.startTime").value("10:00:00"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void theOwnAppointmentsListIsPaginated() throws Exception {
        when(appointmentQueryService.listOwn(any())).thenReturn(new PagedResponse<>(
                List.of(new AppointmentListItem(UUID.randomUUID().toString(), "Dr. Sharma",
                        LocalDate.of(2026, 8, 20), LocalTime.of(10, 0),
                        AppointmentStatus.CONFIRMED, PaymentStatus.PAID)),
                0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/appointments/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].doctorName").value("Dr. Sharma"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void aDoctorCannotOpenThePatientAppointmentList() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/my"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void completingAnAppointmentReportsFollowUpEligibility() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentQueryService.complete(appointmentId)).thenReturn(
                new AppointmentStatusResponse(appointmentId.toString(), AppointmentStatus.COMPLETED, true));

        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment marked as completed"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.followUpEligible").value(true));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientCannotCompleteAnAppointment() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/" + UUID.randomUUID() + "/complete"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void anUnknownAppointmentIs404() throws Exception {
        when(appointmentQueryService.get(any())).thenThrow(new AppointmentNotFoundException());

        mockMvc.perform(get("/api/v1/appointments/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_NOT_FOUND"));
    }
}

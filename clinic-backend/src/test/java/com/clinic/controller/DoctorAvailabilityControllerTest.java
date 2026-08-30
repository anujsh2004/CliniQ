package com.clinic.controller;

import com.clinic.dto.response.AvailabilityResponse;
import com.clinic.dto.response.DoctorSlotsResponse;
import com.clinic.dto.response.SlotSummary;
import com.clinic.entity.SlotStatus;
import com.clinic.security.JwtService;
import com.clinic.service.AvailabilityService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Availability and slot endpoints (API contract 11), including the locked slot
 * response DTO.
 */
@WebMvcTest(DoctorAvailabilityController.class)
@Import(SecuritySliceTestConfig.class)
class DoctorAvailabilityControllerTest {

    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void aDoctorDefinesAvailability() throws Exception {
        when(availabilityService.create(eq(DOCTOR_ID), any())).thenReturn(new AvailabilityResponse(
                UUID.randomUUID().toString(), DOCTOR_ID.toString(), DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30));

        mockMvc.perform(post("/api/v1/doctors/" + DOCTOR_ID + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek": "MONDAY",
                                  "startTime": "09:00:00",
                                  "endTime": "17:00:00",
                                  "slotDurationMinutes": 30
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Availability created successfully"))
                .andExpect(jsonPath("$.data.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("17:00:00"))
                .andExpect(jsonPath("$.data.slotDurationMinutes").value(30));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientCannotDefineAvailability() throws Exception {
        mockMvc.perform(post("/api/v1/doctors/" + DOCTOR_ID + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dayOfWeek": "MONDAY", "startTime": "09:00:00",
                                  "endTime": "17:00:00", "slotDurationMinutes": 30 }"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void theSlotResponseKeepsDateAtTheTopLevelOnly() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(availabilityService.slotsFor(eq(DOCTOR_ID), eq(date))).thenReturn(new DoctorSlotsResponse(
                DOCTOR_ID.toString(), date, List.of(
                        new SlotSummary(UUID.randomUUID().toString(), LocalTime.of(10, 0), LocalTime.of(10, 30),
                                SlotStatus.AVAILABLE),
                        new SlotSummary(UUID.randomUUID().toString(), LocalTime.of(10, 30), LocalTime.of(11, 0),
                                SlotStatus.BOOKED))));

        mockMvc.perform(get("/api/v1/doctors/" + DOCTOR_ID + "/slots").param("date", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(DOCTOR_ID.toString()))
                .andExpect(jsonPath("$.data.date").value("2026-08-20"))
                .andExpect(jsonPath("$.data.slots[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$.data.slots[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.slots[1].status").value("BOOKED"))
                // The contract locks this: date appears once, at the top level,
                // and is never repeated inside a slot object.
                .andExpect(jsonPath("$.data.slots[0].date").doesNotExist())
                .andExpect(jsonPath("$.data.slots[1].date").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aMissingDateParameterIs422() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/" + DOCTOR_ID + "/slots"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'date')]").exists());
    }
}

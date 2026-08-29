package com.clinic.controller;

import com.clinic.dto.response.ClinicSummary;
import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.DoctorSummary;
import com.clinic.dto.response.PagedResponse;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.security.JwtService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import com.clinic.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Doctor endpoints against API contract 9: the list view stays light, the
 * detail view carries the full clinic block, and only admins and doctors may
 * create profiles.
 */
@WebMvcTest(DoctorController.class)
@Import(SecuritySliceTestConfig.class)
class DoctorControllerTest {

    private static final String CREATE_PAYLOAD = """
            {
              "name": "Dr. Sharma",
              "specialization": "Dentist",
              "licenseNumber": "LIC-12345",
              "consultationFee": 500,
              "clinic": {
                "name": "Sharma Dental Clinic",
                "address": "MG Road, Chennai",
                "phone": "+919876543210"
              }
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorService doctorService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateADoctor() throws Exception {
        when(doctorService.create(any())).thenReturn(new DoctorResponse(
                UUID.randomUUID().toString(), "Dr. Sharma", "Dentist", new BigDecimal("500.00"),
                ClinicSummary.withoutPhone(UUID.randomUUID().toString(), "Sharma Dental Clinic",
                        "MG Road, Chennai")));

        mockMvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON).content(CREATE_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Doctor created successfully"))
                .andExpect(jsonPath("$.data.doctorId").exists())
                .andExpect(jsonPath("$.data.clinic.name").value("Sharma Dental Clinic"))
                // The creation response carries no clinic phone, per contract 9.
                .andExpect(jsonPath("$.data.clinic.phone").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientCannotCreateADoctor() throws Exception {
        mockMvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON).content(CREATE_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void anAnonymousCallerCannotBrowseDoctors() throws Exception {
        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void theListViewIsPaginatedAndCarriesNoClinicBlock() throws Exception {
        when(doctorService.list(any())).thenReturn(new PagedResponse<>(
                List.of(new DoctorSummary(UUID.randomUUID().toString(), "Dr. Sharma", "Dentist",
                        new BigDecimal("500.00"))),
                0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Dr. Sharma"))
                .andExpect(jsonPath("$.data.content[0].clinic").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void theDetailViewCarriesTheFullClinicBlock() throws Exception {
        UUID doctorId = UUID.randomUUID();
        when(doctorService.get(doctorId)).thenReturn(new DoctorResponse(
                doctorId.toString(), "Dr. Sharma", "Dentist", new BigDecimal("500.00"),
                new ClinicSummary(UUID.randomUUID().toString(), "Sharma Dental Clinic", "MG Road, Chennai",
                        "+919876543210")));

        mockMvc.perform(get("/api/v1/doctors/" + doctorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinic.phone").value("+919876543210"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void anUnknownDoctorIs404() throws Exception {
        when(doctorService.get(any())).thenThrow(new DoctorNotFoundException());

        mockMvc.perform(get("/api/v1/doctors/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCTOR_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anInvalidCreatePayloadIs422WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "name": "",
                          "specialization": "",
                          "licenseNumber": "",
                          "consultationFee": -5,
                          "clinic": { "name": "", "address": "", "phone": "nope" }
                        }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'consultationFee')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'clinic.phone')]").exists());
    }
}

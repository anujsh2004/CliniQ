package com.clinic.controller;

import com.clinic.dto.response.PatientProfileResponse;
import com.clinic.dto.response.PatientUpdateResponse;
import com.clinic.security.JwtService;
import com.clinic.service.PatientService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Patient profile endpoints (API contract 10). The profile is reachable only as
 * /me, and only by a patient.
 */
@WebMvcTest(PatientController.class)
@Import(SecuritySliceTestConfig.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientReadsTheirOwnProfile() throws Exception {
        when(patientService.getOwnProfile()).thenReturn(new PatientProfileResponse(
                UUID.randomUUID().toString(), "Anuj Kumar", "anuj@example.com", "+919876543210"));

        mockMvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Patient profile fetched successfully"))
                .andExpect(jsonPath("$.data.patientId").exists())
                .andExpect(jsonPath("$.data.email").value("anuj@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientUpdatesTheirOwnProfile() throws Exception {
        when(patientService.updateOwnProfile(any())).thenReturn(new PatientUpdateResponse(
                UUID.randomUUID().toString(), "Anuj Kumar", "+919876543211"));

        mockMvc.perform(put("/api/v1/patients/me").contentType(MediaType.APPLICATION_JSON).content("""
                        { "name": "Anuj Kumar", "phone": "+919876543211" }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+919876543211"))
                // The contract's update response has no email field.
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void aDoctorCannotReadAPatientProfile() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void anAnonymousCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void anInvalidUpdateIs422WithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/patients/me").contentType(MediaType.APPLICATION_JSON).content("""
                        { "name": "", "phone": "not-a-phone" }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists());
    }
}

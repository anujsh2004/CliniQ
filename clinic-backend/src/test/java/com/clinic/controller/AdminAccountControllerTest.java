package com.clinic.controller;

import com.clinic.dto.response.RegisterResponse;
import com.clinic.entity.Role;
import com.clinic.security.JwtService;
import com.clinic.service.AuthService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may create a staff account (contract v1.2, decision D1).
 */
@WebMvcTest(AdminAccountController.class)
@Import(SecuritySliceTestConfig.class)
class AdminAccountControllerTest {

    private static final String DOCTOR_ACCOUNT = """
            {
              "name": "Dr. Meera Sharma",
              "email": "meera@example.com",
              "phone": "+919876543211",
              "password": "StrongPassword123",
              "role": "DOCTOR"
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdministratorCanCreateAStaffAccount() throws Exception {
        when(authService.createStaffAccount(any())).thenReturn(new RegisterResponse(
                UUID.randomUUID().toString(), "Dr. Meera Sharma", "meera@example.com",
                "+919876543211", Role.DOCTOR));

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(DOCTOR_ACCOUNT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientCannotCreateStaffAccounts() throws Exception {
        // The whole point of D1: no path from a patient account to a doctor or
        // administrator one.
        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(DOCTOR_ACCOUNT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));

        verify(authService, never()).createStaffAccount(any());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void aDoctorCannotCreateStaffAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(DOCTOR_ACCOUNT))
                .andExpect(status().isForbidden());

        verify(authService, never()).createStaffAccount(any());
    }

    @Test
    void anAnonymousCallerCannotCreateStaffAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(DOCTOR_ACCOUNT))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).createStaffAccount(any());
    }
}

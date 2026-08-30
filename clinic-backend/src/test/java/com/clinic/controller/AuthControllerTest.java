package com.clinic.controller;

import com.clinic.dto.response.RegisterResponse;
import com.clinic.entity.Role;
import com.clinic.exception.DuplicateEmailException;
import com.clinic.security.JwtService;
import com.clinic.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth endpoints must speak the envelope and the canonical error codes from
 * API contract 7 / 7a, and must never echo a password back.
 */
@WebMvcTest(AuthController.class)
@Import(SecuritySliceTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void registerReturns201WithTheStandardEnvelope() throws Exception {
        when(authService.register(any())).thenReturn(new RegisterResponse(
                UUID.randomUUID().toString(), "Anuj Kumar", "anuj@example.com", "+919876543210", Role.PATIENT));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anuj Kumar",
                                  "email": "anuj@example.com",
                                  "phone": "+919876543210",
                                  "password": "StrongPassword123",
                                  "role": "PATIENT"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void registerReturns422WithFieldErrorsForAnInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "not-an-email",
                                  "phone": "abc",
                                  "password": "short",
                                  "role": "PATIENT"
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void registerReturns409ForADuplicateEmail() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anuj Kumar",
                                  "email": "anuj@example.com",
                                  "phone": "+919876543210",
                                  "password": "StrongPassword123",
                                  "role": "PATIENT"
                                }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
    }

    @Test
    void loginReturns422WhenCredentialsAreMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}

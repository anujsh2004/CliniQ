package com.clinic;

import com.clinic.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.clinic.security.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transport-level failures must still come back in the standard error envelope
 * (API contract 7) with a correct status code, never as a generic 500.
 */
@WebMvcTest(HealthController.class)
@Import(SecuritySliceTestConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // The security filter chain is part of the slice; its JWT collaborator is not.
    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "PATIENT")
    void unknownRouteReturns404InStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Requested resource was not found"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void anonymousCallersGet401RatherThanARouteExistenceHint() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void unsupportedMethodReturns405InStandardEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").exists());
    }
}

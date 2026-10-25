package com.clinic.controller;

import com.clinic.ml.ConditionClient;
import com.clinic.security.JwtService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/symptoms (API contract 27, v1.6). The property matters: the
 * controller exists only with the ML service enabled.
 */
@WebMvcTest(SymptomController.class)
@Import(SecuritySliceTestConfig.class)
@TestPropertySource(properties = "clinic.ml.enabled=true")
class SymptomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConditionClient conditionClient;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "PATIENT")
    void listsTheModelsSymptomsInTheEnvelope() throws Exception {
        when(conditionClient.symptoms()).thenReturn(new ConditionClient.SymptomsResponse("v1",
                List.of(new ConditionClient.Symptom("skin_rash", "Skin rash"))));

        mockMvc.perform(get("/api/v1/symptoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symptoms[0].code").value("skin_rash"))
                .andExpect(jsonPath("$.data.symptoms[0].label").value("Skin rash"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aServiceThatIsDownIs503() throws Exception {
        when(conditionClient.symptoms()).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/api/v1/symptoms"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/symptoms"))
                .andExpect(status().isUnauthorized());
    }
}

package com.clinic.controller;

import com.clinic.dto.response.NotificationQueuedResponse;
import com.clinic.entity.NotificationStatus;
import com.clinic.security.JwtService;
import com.clinic.service.ReminderService;
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
 * The internal reminder endpoint (API contract 15). It is an operational tool,
 * so who can reach it matters as much as what it returns.
 */
@WebMvcTest(InternalNotificationController.class)
@Import(SecuritySliceTestConfig.class)
class InternalNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReminderService reminderService;

    @MockitoBean
    private JwtService jwtService;

    private String payload() {
        return "{\"appointmentId\":\"" + UUID.randomUUID() + "\"}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminCanQueueAReminder() throws Exception {
        when(reminderService.queueReminder(any())).thenReturn(
                new NotificationQueuedResponse(UUID.randomUUID().toString(), NotificationStatus.QUEUED));

        mockMvc.perform(post("/api/v1/internal/notifications/reminders")
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reminder queued successfully"))
                .andExpect(jsonPath("$.data.notificationId").exists())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void aPatientCannotTriggerReminders() throws Exception {
        // Otherwise anyone with an account could make the clinic message people.
        mockMvc.perform(post("/api/v1/internal/notifications/reminders")
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));

        verify(reminderService, never()).queueReminder(any());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void aDoctorCannotTriggerReminders() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notifications/reminders")
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerCannotTriggerReminders() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notifications/reminders")
                        .contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aRequestWithoutAnAppointmentIs422() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notifications/reminders")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'appointmentId')]").exists());
    }
}

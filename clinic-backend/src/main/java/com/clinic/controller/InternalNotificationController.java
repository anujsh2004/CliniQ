package com.clinic.controller;

import com.clinic.dto.request.ReminderEventRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.NotificationQueuedResponse;
import com.clinic.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal notification endpoints (API contract 15).
 *
 * <p>Restricted to ADMIN: this is an operational tool for re-queueing a
 * reminder, not something a patient or the public should be able to trigger.
 * The path is under /internal for the same reason, so a reverse proxy can block
 * it from the outside entirely.
 */
@RestController
@RequestMapping("/api/v1/internal/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class InternalNotificationController {

    private final ReminderService reminderService;

    public InternalNotificationController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping("/reminders")
    public ResponseEntity<ApiResponse<NotificationQueuedResponse>> queueReminder(
            @Valid @RequestBody ReminderEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reminder queued successfully",
                reminderService.queueReminder(request)));
    }
}

package com.clinic.controller;

import com.clinic.dto.request.PatientReminderRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.NotificationSummary;
import com.clinic.service.InAppReminderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The patient's own reminders (API contract 15, v1.4).
 *
 * <p>Reminders are shown here rather than sent through WhatsApp, for which no
 * provider is configured. Everything is scoped to the calling patient: there is
 * no way to read anyone else's.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('PATIENT')")
public class NotificationController {

    private final InAppReminderService reminderService;

    public NotificationController(InAppReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationSummary>>> mine() {
        return ResponseEntity.ok(ApiResponse.success("Reminders fetched successfully",
                reminderService.mine()));
    }

    /** Drives the unread count on the bell, so it needs to stay cheap. */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched successfully",
                Map.of("unread", reminderService.unreadCount())));
    }

    @PostMapping("/reminders")
    public ResponseEntity<ApiResponse<NotificationSummary>> addReminder(
            @Valid @RequestBody PatientReminderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Reminder added successfully",
                reminderService.addPatientReminder(request.appointmentId(),
                        Duration.ofMinutes(request.minutesBefore()))));
    }

    @DeleteMapping("/reminders/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> removeReminder(@PathVariable UUID notificationId) {
        reminderService.removePatientReminder(notificationId);
        return ResponseEntity.ok(ApiResponse.success("Reminder removed successfully"));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID notificationId) {
        reminderService.markRead(notificationId);
        return ResponseEntity.ok(ApiResponse.success("Reminder marked as read"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        reminderService.markAllRead();
        return ResponseEntity.ok(ApiResponse.success("All reminders marked as read"));
    }
}

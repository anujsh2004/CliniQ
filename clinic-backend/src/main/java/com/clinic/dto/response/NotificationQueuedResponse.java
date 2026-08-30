package com.clinic.dto.response;

import com.clinic.entity.NotificationStatus;

/**
 * The worker result shape from API contract 15.
 */
public record NotificationQueuedResponse(String notificationId, NotificationStatus status) {
}

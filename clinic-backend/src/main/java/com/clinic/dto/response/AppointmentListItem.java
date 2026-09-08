package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * One row of GET /api/v1/appointments/my (API contract 12). Flatter than the
 * detail view: the doctor is a name, not a block.
 */
public record AppointmentListItem(
        String appointmentId,
        String doctorName,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        AppointmentStatus status,
        PaymentStatus paymentStatus,

        /**
         * When this appointment's hold on its slot runs out (v1.4). Null once
         * it is paid for: a confirmed appointment owns its slot outright, and
         * the client shows no countdown.
         */
        OffsetDateTime holdExpiresAt) {
}

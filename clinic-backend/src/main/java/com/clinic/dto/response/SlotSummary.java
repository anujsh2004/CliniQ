package com.clinic.dto.response;

import com.clinic.entity.SlotStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * One slot inside the locked slot response DTO (API contract 11).
 *
 * <p>Note what is absent: the date. The contract locks it to the top level of
 * the response and forbids repeating it per slot.
 */
public record SlotSummary(
        String slotId,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        SlotStatus status) {
}

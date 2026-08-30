package com.clinic.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * The locked slot response DTO (API contract 11): doctorId and date once at the
 * top level, then the slots.
 */
public record DoctorSlotsResponse(
        String doctorId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        List<SlotSummary> slots) {
}

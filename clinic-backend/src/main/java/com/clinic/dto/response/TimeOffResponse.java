package com.clinic.dto.response;

import java.time.LocalDate;

/**
 * The result of blocking a date (API contract 11, v1.3).
 *
 * <p>{@code appointmentsToReschedule} is the point of the response: booked
 * slots are not cancelled behind a patient's back, so the doctor is told how
 * many people still need contacting.
 */
public record TimeOffResponse(LocalDate date, int slotsBlocked, int appointmentsToReschedule) {
}

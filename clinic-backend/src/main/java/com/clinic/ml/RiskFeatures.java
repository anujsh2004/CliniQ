package com.clinic.ml;

/**
 * One appointment as the model sees it. Field names are the ML service's wire
 * format; ml-service/features.py documents what each one means and why these
 * and no others.
 *
 * @param appointmentWeekday Monday is 0, Sunday 6
 * @param bookingHour        hour the booking was made, clinic time
 * @param priorVisits        earlier appointments that were completed or missed
 */
public record RiskFeatures(
        String appointmentId,
        int leadDays,
        int appointmentWeekday,
        int bookingHour,
        int priorVisits,
        int priorNoShows,
        boolean reminderSent) {
}

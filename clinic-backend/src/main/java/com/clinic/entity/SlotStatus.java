package com.clinic.entity;

/**
 * Slot lifecycle states from API contract 17.
 */
public enum SlotStatus {
    /** Bookable. */
    AVAILABLE,
    /** Reserved briefly while a booking is being completed. */
    HELD,
    /** Taken by an appointment. */
    BOOKED,
    /** Withheld by the clinic. */
    BLOCKED,
    /** In the past and never booked. */
    EXPIRED
}

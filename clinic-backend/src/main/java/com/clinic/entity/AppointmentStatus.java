package com.clinic.entity;

/**
 * Appointment lifecycle states from API contract 17.
 */
public enum AppointmentStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** An appointment still holding its slot. */
    public boolean isActive() {
        return this == PENDING_PAYMENT || this == CONFIRMED;
    }
}

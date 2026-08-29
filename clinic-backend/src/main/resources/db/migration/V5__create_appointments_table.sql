-- Appointments: the patient/doctor/slot relationship (API contract section 18).

CREATE TABLE appointments (
    id                  UUID         PRIMARY KEY,
    patient_id          UUID         NOT NULL REFERENCES patients (id),
    doctor_id           UUID         NOT NULL REFERENCES doctors (id),
    slot_id             UUID         NOT NULL REFERENCES slots (id),
    status              VARCHAR(20)  NOT NULL,
    payment_status      VARCHAR(20)  NOT NULL,
    reason              VARCHAR(500),
    cancellation_reason VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_appointments_status CHECK (status IN
        ('PENDING_PAYMENT','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT ck_appointments_payment_status CHECK (payment_status IN
        ('PENDING','CREATED','PAID','FAILED','REFUNDED'))
);

-- The no-double-booking guarantee, enforced by the database itself: a slot can
-- be held by at most one appointment that is still live. A cancelled
-- appointment releases its slot, so the slot can be booked again, which is why
-- this is a partial index rather than a plain unique constraint.
CREATE UNIQUE INDEX ux_appointments_active_slot
    ON appointments (slot_id)
    WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED');

-- Hot read paths: a patient's appointment history and a doctor's daily list.
CREATE INDEX ix_appointments_patient ON appointments (patient_id);
CREATE INDEX ix_appointments_doctor ON appointments (doctor_id);

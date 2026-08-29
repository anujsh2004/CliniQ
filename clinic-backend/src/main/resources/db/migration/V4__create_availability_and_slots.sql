-- Recurring weekly availability and the concrete slots generated from it
-- (API contract sections 11 and 18).

CREATE TABLE doctor_availability (
    id                    UUID        PRIMARY KEY,
    doctor_id             UUID        NOT NULL REFERENCES doctors (id),
    day_of_week           VARCHAR(10) NOT NULL,
    start_time            TIME        NOT NULL,
    end_time              TIME        NOT NULL,
    slot_duration_minutes INTEGER     NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_availability_day CHECK (day_of_week IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT ck_availability_time_order CHECK (start_time < end_time),
    CONSTRAINT ck_availability_duration CHECK (slot_duration_minutes > 0)
);

CREATE INDEX ix_availability_doctor_day ON doctor_availability (doctor_id, day_of_week);

CREATE TABLE slots (
    id         UUID        PRIMARY KEY,
    doctor_id  UUID        NOT NULL REFERENCES doctors (id),
    slot_date  DATE        NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_slots_status CHECK (status IN ('AVAILABLE','HELD','BOOKED','BLOCKED','EXPIRED')),
    CONSTRAINT ck_slots_time_order CHECK (start_time < end_time),
    -- Defence in depth under the row lock taken on the booking path: one doctor
    -- cannot have two slots starting at the same moment on the same day.
    CONSTRAINT uq_slots_doctor_date_start UNIQUE (doctor_id, slot_date, start_time)
);

-- Slot availability lookup is the hottest read path in the product.
CREATE INDEX ix_slots_doctor_date ON slots (doctor_id, slot_date);
CREATE INDEX ix_slots_status ON slots (status);

-- Reminder delivery records (API contract sections 15 and 18).

CREATE TABLE notifications (
    id             UUID         PRIMARY KEY,
    appointment_id UUID         NOT NULL REFERENCES appointments (id),
    channel        VARCHAR(20)  NOT NULL,
    reminder_type  VARCHAR(20)  NOT NULL,
    scheduled_for  TIMESTAMPTZ  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_notifications_status CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED')),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('WHATSAPP'))
);

-- One reminder of a given type per appointment: the scheduler runs repeatedly
-- and must not queue the same 24-hour reminder on every pass.
CREATE UNIQUE INDEX ux_notifications_appointment_type
    ON notifications (appointment_id, reminder_type);

CREATE INDEX ix_notifications_status ON notifications (status);

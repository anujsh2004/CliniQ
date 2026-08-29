-- Gateway orders and their outcomes (API contract sections 14 and 18).

CREATE TABLE payments (
    id                 UUID           PRIMARY KEY,
    appointment_id     UUID           NOT NULL REFERENCES appointments (id),
    gateway            VARCHAR(20)    NOT NULL,
    order_id           VARCHAR(100)   NOT NULL,
    gateway_payment_id VARCHAR(100),
    amount             NUMERIC(10, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_status CHECK (status IN
        ('PENDING','CREATED','PAID','FAILED','REFUNDED')),
    CONSTRAINT ck_payments_amount_non_negative CHECK (amount >= 0)
);

-- An appointment can be paid at most once. A second order for an appointment
-- that is already paid is refused by the database, not just by the service.
CREATE UNIQUE INDEX ux_payments_paid_appointment
    ON payments (appointment_id)
    WHERE status = 'PAID';

CREATE INDEX ix_payments_appointment ON payments (appointment_id);

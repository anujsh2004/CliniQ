-- A booked slot is held, not sold, until it is paid for (API contract 12, v1.4).
--
-- The hold is what keeps a slot off the market while one patient completes
-- payment, and what returns it to the market when they do not.

ALTER TABLE appointments
    ADD COLUMN hold_expires_at TIMESTAMPTZ;

-- The expiry sweep looks for live appointments whose hold has run out, so it
-- reads by status and expiry rather than scanning the table.
CREATE INDEX ix_appointments_hold_expiry
    ON appointments (status, hold_expires_at)
    WHERE hold_expires_at IS NOT NULL;

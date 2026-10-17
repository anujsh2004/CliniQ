-- A reminder has no meaning without the appointment it is about, so it should
-- not outlive it or block its removal.
--
-- Nothing deletes appointments in normal use - cancelling sets a status - but
-- the constraint was strict enough to make an appointment undeletable at all,
-- which is wrong on its own terms and blocked test cleanup.

ALTER TABLE notifications
    DROP CONSTRAINT notifications_appointment_id_fkey;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_appointment_id_fkey
    FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE;

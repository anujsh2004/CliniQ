-- In-app reminders (API contract 15, v1.4).
--
-- Reminders are shown in the patient's own notification list rather than sent
-- through WhatsApp, for which no provider is configured.

ALTER TABLE notifications
    DROP CONSTRAINT ck_notifications_channel;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_channel CHECK (channel IN ('IN_APP', 'WHATSAPP'));

-- The message the patient actually reads. Composed when the reminder is
-- queued, so the wording a patient saw is preserved even if the template
-- changes later.
ALTER TABLE notifications
    ADD COLUMN message VARCHAR(500);

-- When the patient dismissed it, and null while unread. The bell counts these.
ALTER TABLE notifications
    ADD COLUMN read_at TIMESTAMPTZ;

-- Whether the patient asked for this reminder themselves, as opposed to the
-- clinic's defaults. A patient may remove their own; the clinic's stay.
ALTER TABLE notifications
    ADD COLUMN patient_requested BOOLEAN NOT NULL DEFAULT FALSE;

-- The list a patient sees is "my reminders, most recent first", so it reads by
-- patient and time rather than scanning.
CREATE INDEX ix_notifications_due
    ON notifications (status, scheduled_for);

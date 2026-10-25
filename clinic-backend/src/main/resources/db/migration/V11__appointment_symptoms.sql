-- Symptoms a patient reports when booking (API contract 27, v1.6).
--
-- One row per symptom rather than a delimited column, so a symptom can be
-- counted or searched without string parsing. Codes are the ML service's
-- vocabulary (e.g. 'high_fever'); the label a person reads is derived from the
-- code, so it is not stored.

CREATE TABLE appointment_symptoms (
    appointment_id UUID        NOT NULL REFERENCES appointments (id) ON DELETE CASCADE,
    symptom        VARCHAR(60) NOT NULL,
    CONSTRAINT pk_appointment_symptoms PRIMARY KEY (appointment_id, symptom),
    CONSTRAINT ck_appointment_symptoms_code CHECK (symptom ~ '^[a-z0-9_]+$')
);

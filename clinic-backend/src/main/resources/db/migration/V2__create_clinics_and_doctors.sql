-- Clinics and doctor profiles (API contract section 18).

CREATE TABLE clinics (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- One clinic is identified by its name at an address; creating a second doctor
-- for the same clinic reuses the row instead of duplicating it.
CREATE UNIQUE INDEX ux_clinics_name_address ON clinics (LOWER(name), LOWER(address));

CREATE TABLE doctors (
    id               UUID           PRIMARY KEY,
    user_id          UUID           REFERENCES users (id),
    clinic_id        UUID           NOT NULL REFERENCES clinics (id),
    name             VARCHAR(100)   NOT NULL,
    specialization   VARCHAR(100)   NOT NULL,
    license_number   VARCHAR(50)    NOT NULL,
    consultation_fee NUMERIC(10, 2) NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_doctors_license UNIQUE (license_number),
    CONSTRAINT uq_doctors_user UNIQUE (user_id),
    CONSTRAINT ck_doctors_fee_non_negative CHECK (consultation_fee >= 0)
);

-- The doctor list is browsed by patients and filtered by clinic for admins.
CREATE INDEX ix_doctors_clinic ON doctors (clinic_id);
CREATE INDEX ix_doctors_specialization ON doctors (LOWER(specialization));

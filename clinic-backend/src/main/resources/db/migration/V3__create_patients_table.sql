-- Patient profiles (API contract section 18). One row per patient account; the
-- name, email and phone the contract returns live on the users table.

CREATE TABLE patients (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_patients_user UNIQUE (user_id)
);

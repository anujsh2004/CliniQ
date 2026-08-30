-- Users: identity, credentials and role (API contract section 18).
-- Passwords are stored only as bcrypt hashes.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT ck_users_role CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN'))
);

-- Login looks users up by email; the lookup is case-insensitive so
-- Anuj@example.com and anuj@example.com resolve to the same account.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

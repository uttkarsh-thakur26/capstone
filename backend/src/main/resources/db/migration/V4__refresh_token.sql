-- Refresh tokens (AUTH-6, ADR-020). Only the SHA-256 of a token is stored. Every token rotated
-- from one sign-in shares a family_id, so reuse of a rotated token can revoke the whole family.
CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash text        NOT NULL,
    family_id  uuid        NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_family_id_idx ON refresh_token (family_id);

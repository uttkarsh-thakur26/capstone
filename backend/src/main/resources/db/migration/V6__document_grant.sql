-- Who may read a document besides its owner and admins (ACL-1, ACL-5, specification 7.2, ADR-030).
-- Grants are replaced as a set and never changed, so there is no updated_at.
CREATE TABLE document_grant (
    id              uuid        PRIMARY KEY,
    document_id     uuid        NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    grantee_type    text        NOT NULL,
    grantee_role    text,
    grantee_user_id uuid        REFERENCES app_user (id) ON DELETE CASCADE,
    created_by      uuid        REFERENCES app_user (id) ON DELETE SET NULL,
    created_at      timestamptz NOT NULL,
    CONSTRAINT document_grant_grantee_type_check CHECK (grantee_type IN ('ROLE', 'USER')),
    -- Admins read every document (D15), so a grant to ADMIN would mean nothing.
    CONSTRAINT document_grant_grantee_role_check CHECK (grantee_role IN ('FACULTY', 'STUDENT')),
    -- Exactly one grantee column is set, the one that matches the type.
    CONSTRAINT document_grant_grantee_check CHECK (
        (grantee_type = 'ROLE' AND grantee_role IS NOT NULL AND grantee_user_id IS NULL)
        OR (grantee_type = 'USER' AND grantee_user_id IS NOT NULL AND grantee_role IS NULL)),
    -- One grant per document and grantee. NULLS NOT DISTINCT makes the unset grantee column
    -- count as equal, so two grants to the same role collide. This index also serves the
    -- lookups by document.
    CONSTRAINT document_grant_document_id_grantee_key
        UNIQUE NULLS NOT DISTINCT (document_id, grantee_type, grantee_role, grantee_user_id)
);

CREATE INDEX document_grant_grantee_user_id_idx ON document_grant (grantee_user_id);

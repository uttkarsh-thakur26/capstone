-- Stored files still to be deleted (DOC-9, ADR-031). The transaction that deletes a document's
-- rows adds its file here; the file is deleted after commit and the row removed. A scheduled task
-- retries whatever is left, so a failed deletion survives a restart. Written through JdbcClient.
CREATE TABLE storage_deletion (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_key     text        NOT NULL,
    requested_at    timestamptz NOT NULL,
    attempts        integer     NOT NULL,
    last_attempt_at timestamptz,
    CONSTRAINT storage_deletion_storage_key_key UNIQUE (storage_key)
);

-- Stored summaries (SUM-4, SUM-5, ADR-040): one row per document, model and prompt version, written
-- through JdbcClient. A summary is served only while source_indexed_at equals the document's
-- indexed_at, so re-indexing makes it out of date without a trigger or a delete; deleting the
-- document deletes its summaries (DATA-2). Serving one still needs a fresh access check (ACL-E5),
-- which the service makes before it reads this table.
CREATE TABLE document_summary (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    document_id         uuid        NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    model               text        NOT NULL,
    prompt_version      text        NOT NULL,
    status              text        NOT NULL,
    content             jsonb,
    failure_message     text,
    source_indexed_at   timestamptz NOT NULL,
    requested_by        uuid        REFERENCES app_user (id) ON DELETE SET NULL,
    model_calls         integer     NOT NULL DEFAULT 0,
    input_tokens        integer,
    cached_input_tokens bigint,
    output_tokens       integer,
    started_at          timestamptz NOT NULL,
    finished_at         timestamptz,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CONSTRAINT document_summary_status_check CHECK (status IN ('PENDING', 'READY', 'FAILED')),
    CONSTRAINT document_summary_content_check CHECK ((status = 'READY') = (content IS NOT NULL)),
    CONSTRAINT document_summary_unique UNIQUE (document_id, model, prompt_version)
);

CREATE INDEX document_summary_requested_by ON document_summary (requested_by);

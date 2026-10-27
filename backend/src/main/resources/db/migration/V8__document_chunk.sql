-- Chunks of indexed documents with their vectors (specification 7.2, ADR-001, ADR-034). Written
-- only by ingestion, read only by retrieval (4.3). A document's chunks are replaced as a set in one
-- transaction (ING-3) and never updated, so the table has created_at and no updated_at.
-- The dimension comes from app.embedding.dimensions through a Flyway placeholder (DATA-4); the
-- application compares it with this column at startup, because Flyway does not notice a changed
-- placeholder on a database that is already migrated (S11).
CREATE TABLE document_chunk (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id uuid        NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    chunk_index integer     NOT NULL,
    page_start  integer     NOT NULL,
    page_end    integer     NOT NULL,
    section     text,
    content     text        NOT NULL,
    token_count integer     NOT NULL,
    embedding   vector(${embedding_dim}) NOT NULL,
    created_at  timestamptz NOT NULL,
    -- Also serves the lookups by document, so document_id gets no index of its own (ADR-034).
    CONSTRAINT document_chunk_document_id_chunk_index_key UNIQUE (document_id, chunk_index),
    CONSTRAINT document_chunk_chunk_index_check CHECK (chunk_index >= 0),
    CONSTRAINT document_chunk_pages_check CHECK (page_start >= 1 AND page_end >= page_start),
    CONSTRAINT document_chunk_token_count_check CHECK (token_count > 0),
    CONSTRAINT document_chunk_content_not_blank CHECK (btrim(content) <> '')
);

-- The lease of a processing run (ADR-034). A claim gives the run a new id; the worker refreshes the
-- heartbeat while the run is alive, and every write of the run names its id, so a run that was
-- recovered or replaced can no longer change the document.
ALTER TABLE document
    ADD COLUMN processing_run_id       uuid,
    ADD COLUMN processing_heartbeat_at timestamptz,
    ADD CONSTRAINT document_processing_run_check CHECK (
        (status = 'PROCESSING') = (processing_run_id IS NOT NULL)
        AND (processing_run_id IS NULL) = (processing_heartbeat_at IS NULL));

-- ING-10: too many chunks is a failure of its own (ADR-034).
ALTER TABLE document DROP CONSTRAINT document_failure_code_check;
ALTER TABLE document ADD CONSTRAINT document_failure_code_check CHECK (failure_code IN (
    'UNSUPPORTED_TYPE', 'EMPTY', 'ENCRYPTED', 'NO_TEXT_LAYER', 'TOO_MANY_PAGES', 'TOO_MANY_CHUNKS',
    'EXTRACTION_ERROR', 'EMBEDDING_ERROR', 'PROCESSING_TIMEOUT', 'INTERNAL'));

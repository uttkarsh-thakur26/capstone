-- Conversations, their messages and the citation snapshots of answers (specification 7.2, 12).
-- A conversation belongs to one user (CONV-1) and goes with the account.
CREATE TABLE conversation (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Null until the first question names it (CONV-3).
    title      text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

-- The newest conversations of a user first (CONV-2).
CREATE INDEX conversation_user_id_updated_at_idx ON conversation (user_id, updated_at);

-- A question or an answer. Messages are never changed, so there is no updated_at (ADR-025).
-- Outcome, model and prompt version belong to answers; model and prompt version stay null when
-- the evidence gate answered without calling the model (ADR-037).
CREATE TABLE message (
    id              uuid        PRIMARY KEY,
    conversation_id uuid        NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role            text        NOT NULL,
    content         text        NOT NULL,
    outcome         text,
    model           text,
    prompt_version  text,
    created_at      timestamptz NOT NULL,
    CONSTRAINT message_role_check CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT message_outcome_check CHECK (outcome IN ('ANSWERED', 'INSUFFICIENT_EVIDENCE', 'ERROR')),
    CONSTRAINT message_assistant_fields_check CHECK (
        (role = 'ASSISTANT' AND outcome IS NOT NULL)
        OR (role = 'USER' AND outcome IS NULL AND model IS NULL AND prompt_version IS NULL))
);

CREATE INDEX message_conversation_id_created_at_idx ON message (conversation_id, created_at);

-- What an answer cited, as a snapshot: chunks are replaced on re-index, so a citation never points
-- at a chunk row (DATA-1). Deleting the document keeps the snapshot and clears the link (DATA-2).
CREATE TABLE message_citation (
    id             uuid             PRIMARY KEY,
    message_id     uuid             NOT NULL REFERENCES message (id) ON DELETE CASCADE,
    ordinal        integer          NOT NULL,
    document_id    uuid             REFERENCES document (id) ON DELETE SET NULL,
    document_title text             NOT NULL,
    page_start     integer          NOT NULL,
    page_end       integer          NOT NULL,
    chunk_index    integer          NOT NULL,
    snippet        text             NOT NULL,
    score          double precision NOT NULL,
    created_at     timestamptz      NOT NULL,
    -- One citation per source number of an answer. Also serves the lookups by message.
    CONSTRAINT message_citation_message_id_ordinal_key UNIQUE (message_id, ordinal),
    CONSTRAINT message_citation_ordinal_check CHECK (ordinal >= 1),
    CONSTRAINT message_citation_pages_check CHECK (page_start >= 1 AND page_end >= page_start)
);

-- Deleting a document sets its citations' document_id to null, which needs this lookup.
CREATE INDEX message_citation_document_id_idx ON message_citation (document_id);

-- One row per search, question, summary and comparison (specification 7.2, 14.3, 21, ADR-041),
-- written through JdbcClient after the request has its outcome. Rows never change, so there is no
-- updated_at. D14 is off: the table has no column for question or query text at all, so storing it
-- later needs a migration and a decision record, not a setting.
CREATE TABLE query_log (
    id                  bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at          timestamptz   NOT NULL,
    -- Users are never deleted (AUTH-11); if that changes, the figures keep the row (S3).
    user_id             uuid          REFERENCES app_user (id) ON DELETE SET NULL,
    type                text          NOT NULL,
    outcome             text          NOT NULL,
    total_ms            integer       NOT NULL,
    retrieval_ms        integer,
    generation_ms       integer,
    retrieved_count     integer,
    -- The best cosine similarity retrieved, the evidence gate's scale (S22); null in keyword mode.
    top_similarity      double precision,
    model               text,
    model_calls         integer       NOT NULL DEFAULT 0,
    input_tokens        integer,
    cached_input_tokens bigint,
    output_tokens       integer,
    -- The row's tokens at the prices configured when it was written (app.assistant.price), so a
    -- later price change does not rewrite past costs.
    cost_usd            numeric(12, 6) NOT NULL DEFAULT 0,
    CONSTRAINT query_log_type_check CHECK (type IN (
        'SEARCH_KEYWORD', 'SEARCH_SEMANTIC', 'SEARCH_HYBRID', 'ASK', 'SUMMARIZE', 'COMPARE')),
    CONSTRAINT query_log_outcome_check CHECK (outcome IN (
        'OK', 'ANSWERED', 'INSUFFICIENT_EVIDENCE', 'REUSED', 'ERROR')),
    CONSTRAINT query_log_times_check CHECK (
        total_ms >= 0 AND (retrieval_ms IS NULL OR retrieval_ms >= 0)
        AND (generation_ms IS NULL OR generation_ms >= 0))
);

CREATE INDEX query_log_created_at_idx ON query_log (created_at);
CREATE INDEX query_log_user_id_idx ON query_log (user_id);

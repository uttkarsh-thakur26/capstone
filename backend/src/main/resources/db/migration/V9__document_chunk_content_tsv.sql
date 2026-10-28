-- Keyword search (RET-5, ADR-035): the words of each chunk as a tsvector, kept by the database
-- itself, so ingestion writes nothing new. English is the corpus language (D13). The column must
-- be STORED: PostgreSQL 18 makes a generated column VIRTUAL by default, and a virtual column cannot
-- be indexed.
ALTER TABLE document_chunk
    ADD COLUMN content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

-- Specification 7.4: the full-text index. Vectors keep no index while exact search is enough (D11).
CREATE INDEX document_chunk_content_tsv_idx ON document_chunk USING gin (content_tsv);

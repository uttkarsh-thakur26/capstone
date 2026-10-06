-- pgvector provides the vector type and the distance operators used by retrieval.
-- The extension is not marked trusted, so this needs a superuser; the database
-- owner created by the container image is one.
CREATE EXTENSION IF NOT EXISTS vector;

package in.edu.bennett.capstone.knowledge.retrieval;

import java.time.Instant;
import java.util.UUID;

/**
 * One chunk of a document, as a summary or a comparison reads it (Section 13). Every field comes
 * from the database (RAG-3).
 *
 * @param indexedAt when the document was last indexed; a stored summary made from other chunks is
 *     out of date (SUM-4)
 * @param tokenCount the chunk's size in the chunker's tokens (cl100k)
 */
public record DocumentChunk(
    UUID documentId,
    String title,
    Instant indexedAt,
    int chunkIndex,
    int pageStart,
    int pageEnd,
    String text,
    int tokenCount) {}

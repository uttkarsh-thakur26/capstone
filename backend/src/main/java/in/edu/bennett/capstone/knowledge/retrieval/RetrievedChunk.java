package in.edu.bennett.capstone.knowledge.retrieval;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A chunk retrieved for a question (11.1 step 4), with what the prompt and a citation snapshot
 * need. Every field comes from the database (RAG-3).
 *
 * @param documentDate the document's own date, null when none was given (11.2)
 * @param similarity {@code 1 - cosine distance} to the question (RET-2)
 */
public record RetrievedChunk(
    UUID documentId,
    String title,
    LocalDate documentDate,
    int chunkIndex,
    int pageStart,
    int pageEnd,
    String text,
    double similarity) {}

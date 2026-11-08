package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.retrieval.SearchResponse.Highlight;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One chunk found by {@link ChunkSearch}, with the title and date of its document and its score.
 *
 * @param documentDate the document's own date, null when none was given
 * @param score what the mode ranks by: similarity, keyword rank or fused score
 * @param similarity the chunk's cosine similarity to the query, {@code 1 - distance}; null in
 *     keyword mode, which embeds no query
 */
record ChunkHit(
    UUID documentId,
    String title,
    LocalDate documentDate,
    int chunkIndex,
    int pageStart,
    int pageEnd,
    String text,
    List<Highlight> highlights,
    double score,
    Double similarity) {}

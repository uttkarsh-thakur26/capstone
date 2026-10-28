package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.retrieval.SearchResponse.Highlight;
import java.util.List;
import java.util.UUID;

/** One chunk found by {@link ChunkSearch}, with the title of its document and its score. */
record ChunkHit(
    UUID documentId,
    String title,
    int pageStart,
    int pageEnd,
    String text,
    List<Highlight> highlights,
    double score) {}

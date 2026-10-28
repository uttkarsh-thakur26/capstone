package in.edu.bennett.capstone.knowledge.retrieval;

/** How chunks are ranked (specification 10, 15.2). {@code HYBRID} joins in Phase 8 (RET-6). */
public enum SearchMode {
  /** By meaning: cosine similarity of the query's vector and each chunk's vector (RET-2). */
  SEMANTIC,
  /** By words: PostgreSQL full-text search (RET-5). */
  KEYWORD
}

package in.edu.bennett.capstone.knowledge.retrieval;

/** How chunks are ranked (specification 10, 15.2). */
public enum SearchMode {
  /** By meaning: cosine similarity of the query's vector and each chunk's vector (RET-2). */
  SEMANTIC,
  /** By words: PostgreSQL full-text search, every word of the query required (RET-5). */
  KEYWORD,
  /**
   * Both rankings fused with Reciprocal Rank Fusion (RET-6, ADR-040): each chunk scores {@code 1 /
   * (60 + rank)} in each list it appears in. Its keyword list puts the chunks with every word of
   * the query first, as keyword mode ranks them, then the chunks with only some of the words.
   */
  HYBRID
}

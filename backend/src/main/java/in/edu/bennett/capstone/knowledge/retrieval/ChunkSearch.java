package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.List;
import java.util.UUID;

/**
 * Chunk search (specification 10), behind an interface (4.4). Every method takes the user whose
 * access applies, the query, the scope and {@code k}, and returns only chunks of documents that
 * user may retrieve, best first (RET-1, RET-7).
 */
interface ChunkSearch {

  /**
   * RET-2, RET-3: the {@code k} chunks closest in meaning to a query vector, by exact search.
   *
   * @param queryVector the query, already embedded: the model call happens before and outside the
   *     read transaction (4.5, S14)
   */
  List<ChunkHit> semantic(CurrentUser user, float[] queryVector, SearchScope scope, int k);

  /** RET-5: the {@code k} chunks that best match the query's words, with the matches marked. */
  List<ChunkHit> keyword(CurrentUser user, String query, SearchScope scope, int k);

  /**
   * RET-6: the {@code k} best chunks of the semantic and keyword rankings fused, each with its
   * similarity to the query and its matched words marked.
   *
   * @param queryVector the query, already embedded outside the read transaction (4.5, S14)
   * @param query the query as typed, for the chunks that hold every word, ranked as keyword mode
   *     ranks them
   * @param anyWords the query's words joined by {@code or}, for the chunks that hold only some of
   *     them, ranked after those ({@link SearchService#anyWords})
   */
  List<ChunkHit> hybrid(
      CurrentUser user,
      float[] queryVector,
      String query,
      String anyWords,
      SearchScope scope,
      int k);

  /**
   * SUM-1, SUM-3: every chunk of a document the user can read and that is indexed, archived or not,
   * in the order of the text; empty when the document is missing, unreadable or not indexed.
   */
  List<DocumentChunk> documentChunks(CurrentUser user, UUID documentId);

  /**
   * CMP-1, CMP-2: the similarity of each chunk of the left document with each chunk of the right
   * one; empty unless the user can read both and both are indexed.
   */
  List<ChunkSimilarity> similarities(CurrentUser user, UUID leftId, UUID rightId);
}

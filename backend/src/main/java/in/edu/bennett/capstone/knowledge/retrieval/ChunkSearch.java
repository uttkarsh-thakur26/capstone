package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.List;

/**
 * Chunk search (specification 10), behind an interface (4.4). Every method takes the user whose
 * access applies, the query, the scope and {@code k}, and returns only chunks of documents that
 * user may retrieve, best first (RET-1, RET-7). Hybrid search joins in Phase 8.
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
}

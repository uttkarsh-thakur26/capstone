package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.ai.Embedder;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingFailedException;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.document.DocumentService;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Search (specification 10). Deliberately not transactional, so the three steps keep 4.5's rule
 * (ADR-035, S14):
 *
 * <ol>
 *   <li>the document ids of the scope are checked against ACL-1, in a short read transaction
 *       (RET-8);
 *   <li>in semantic mode the query is embedded, with no transaction or connection held;
 *   <li>one search statement runs in a read transaction ({@link ChunkSearch}).
 * </ol>
 *
 * A grant removed between the first and the last step still takes effect, because the search
 * statement checks access again (ACL-7).
 */
@Service
public class SearchService {

  private final DocumentService documents;
  private final Embedder embedder;
  private final ChunkSearch chunks;
  private final RetrievalProperties properties;

  SearchService(
      DocumentService documents,
      Embedder embedder,
      ChunkSearch chunks,
      RetrievalProperties properties) {
    this.documents = documents;
    this.embedder = embedder;
    this.chunks = chunks;
    this.properties = properties;
  }

  public SearchResponse search(CurrentUser user, SearchRequest request) {
    SearchScope scope = SearchScope.of(request.documentIds(), request.category());
    documents.requireReadable(user, scope.documentIds());
    int k = request.k() == null ? properties.k() : request.k();
    String query = request.query().strip();
    List<ChunkHit> hits =
        switch (request.mode()) {
          case SEMANTIC -> chunks.semantic(user, embed(query), scope, k);
          case KEYWORD -> chunks.keyword(user, query, scope, k);
        };
    return new SearchResponse(
        request.mode(),
        hits.stream()
            .map(
                hit ->
                    new SearchResponse.SearchResult(
                        hit.documentId(),
                        hit.title(),
                        hit.pageStart(),
                        hit.pageEnd(),
                        hit.text(),
                        hit.highlights(),
                        hit.score()))
            .toList());
  }

  /**
   * RET-2: the query goes through {@link Embedder#embedQuery}, which puts the model's instruction
   * prefix in front of it (ADR-033), with the same model as the index.
   */
  private float[] embed(String query) {
    try {
      return embedder.embedQuery(query);
    } catch (EmbeddingFailedException e) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          "Search by meaning is not available right now. Try again later, or search by keywords.");
    }
  }
}

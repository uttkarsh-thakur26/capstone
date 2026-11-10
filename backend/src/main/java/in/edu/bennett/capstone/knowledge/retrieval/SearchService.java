package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.ai.Embedder;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingFailedException;
import in.edu.bennett.capstone.knowledge.analytics.QueryLog;
import in.edu.bennett.capstone.knowledge.analytics.QueryOutcome;
import in.edu.bennett.capstone.knowledge.analytics.QueryRecord;
import in.edu.bennett.capstone.knowledge.analytics.QueryType;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.document.DocumentService;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Search (specification 10). Deliberately not transactional, so the three steps keep 4.5's rule
 * (ADR-035, S14):
 *
 * <ol>
 *   <li>the document ids of the scope are checked against ACL-1, in a short read transaction
 *       (RET-8);
 *   <li>in semantic and hybrid mode the query is embedded, with no transaction or connection held;
 *   <li>one search statement runs in a read transaction ({@link ChunkSearch}).
 * </ol>
 *
 * A grant removed between the first and the last step still takes effect, because the search
 * statement checks access again (ACL-7).
 */
@Service
public class SearchService {

  /**
   * A word: letters, with their combining marks (the vowel signs of Devanagari, for example), and
   * digits.
   */
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");

  private final DocumentService documents;
  private final Embedder embedder;
  private final ChunkSearch chunks;
  private final RetrievalProperties properties;
  private final QueryLog queryLog;

  SearchService(
      DocumentService documents,
      Embedder embedder,
      ChunkSearch chunks,
      RetrievalProperties properties,
      QueryLog queryLog) {
    this.documents = documents;
    this.embedder = embedder;
    this.chunks = chunks;
    this.properties = properties;
    this.queryLog = queryLog;
  }

  /**
   * One {@code query_log} row per search that ran, with its time and the best similarity; a search
   * refused before it ran (an unreadable scope, 404) has none. No query text (D14).
   */
  public SearchResponse search(CurrentUser user, SearchRequest request) {
    SearchScope scope = SearchScope.of(request.documentIds(), request.category());
    documents.requireReadable(user, scope.documentIds());
    int k = request.k() == null ? properties.k() : request.k();
    String query = request.query().strip();
    long started = System.nanoTime();
    List<ChunkHit> hits;
    try {
      hits =
          switch (request.mode()) {
            case SEMANTIC -> chunks.semantic(user, embed(query, request.mode()), scope, k);
            case KEYWORD -> chunks.keyword(user, query, scope, k);
            case HYBRID ->
                chunks.hybrid(user, embed(query, request.mode()), query, anyWords(query), scope, k);
          };
    } catch (RuntimeException e) {
      long millis = millisSince(started);
      queryLog.record(
          QueryRecord.search(
              user.id(), typeOf(request.mode()), QueryOutcome.ERROR, millis, null, null, null));
      throw e;
    }
    long millis = millisSince(started);
    queryLog.record(
        QueryRecord.search(
            user.id(),
            typeOf(request.mode()),
            QueryOutcome.OK,
            millis,
            millis,
            hits.size(),
            hits.stream()
                .map(ChunkHit::similarity)
                .filter(Objects::nonNull)
                .max(Double::compare)
                .orElse(null)));
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
                        hit.score(),
                        hit.similarity()))
            .toList());
  }

  /**
   * Retrieval for a question (11.1 step 4): the {@code app.retrieval.k} best chunks the user may
   * retrieve, in {@code app.retrieval.assistant-mode} (hybrid by default), through the same three
   * steps as {@link #search}. Every chunk carries its cosine similarity, which the evidence gate
   * compares with its floor in either mode (ADR-040).
   *
   * @param documentIds restrict to these documents; each must be readable, or the answer is 404
   *     (RET-8, CONV-7). Null or empty for all
   * @throws EmbeddingFailedException when the question cannot be embedded
   */
  public List<RetrievedChunk> retrieve(CurrentUser user, String question, List<UUID> documentIds) {
    SearchScope scope = SearchScope.of(documentIds, null);
    documents.requireReadable(user, scope.documentIds());
    String query = question.strip();
    float[] vector = embedder.embedQuery(query);
    List<ChunkHit> hits =
        properties.assistantMode() == SearchMode.SEMANTIC
            ? chunks.semantic(user, vector, scope, properties.k())
            : chunks.hybrid(user, vector, query, anyWords(query), scope, properties.k());
    return hits.stream()
        .map(
            hit ->
                new RetrievedChunk(
                    hit.documentId(),
                    hit.title(),
                    hit.documentDate(),
                    hit.chunkIndex(),
                    hit.pageStart(),
                    hit.pageEnd(),
                    hit.text(),
                    hit.similarity()))
        .toList();
  }

  /**
   * The query's words joined by {@code or}, for {@code websearch_to_tsquery}: the keyword ranking
   * of a hybrid search then needs any of the words instead of all of them, because a question
   * written as a sentence rarely holds every one of its words in one chunk (ADR-040). Only letters,
   * their marks and digits pass, so no quote, minus sign or other operator of the web search syntax
   * reaches the query; the word "or" itself, a stop word, is left out so it cannot be read as an
   * operator.
   */
  private static QueryType typeOf(SearchMode mode) {
    return switch (mode) {
      case SEMANTIC -> QueryType.SEARCH_SEMANTIC;
      case KEYWORD -> QueryType.SEARCH_KEYWORD;
      case HYBRID -> QueryType.SEARCH_HYBRID;
    };
  }

  private static long millisSince(long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
  }

  static String anyWords(String query) {
    List<String> words = new ArrayList<>();
    Matcher word = WORD.matcher(query);
    while (word.find()) {
      if (!word.group().toLowerCase(Locale.ROOT).equals("or")) {
        words.add(word.group());
      }
    }
    return String.join(" or ", words);
  }

  /**
   * RET-2: the query goes through {@link Embedder#embedQuery}, which puts the model's instruction
   * prefix in front of it (ADR-033), with the same model as the index. When the model fails, the
   * message names the mode the reader picked: best match needs the model too.
   */
  private float[] embed(String query, SearchMode mode) {
    try {
      return embedder.embedQuery(query);
    } catch (EmbeddingFailedException e) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          mode == SearchMode.HYBRID
              ? "Best match is not available right now: it needs the search-by-meaning model, which"
                  + " is not running. Try again later, or search by keywords."
              : "Search by meaning is not available right now. Try again later, or search by"
                  + " keywords.");
    }
  }
}

package in.edu.bennett.capstone.knowledge.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;

/**
 * The answer of {@code POST /search}: the best chunks first, at most {@code k}. Not the paged list
 * envelope of 15.1, because search returns the top results rather than pages of a list (ADR-035).
 * An empty list is a normal answer (15.1).
 */
public record SearchResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) SearchMode mode,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<SearchResult> results) {

  /**
   * One chunk (RET-7), from a document the user may retrieve.
   *
   * @param text the whole chunk; the screen shows an excerpt of it
   * @param highlights the words that matched, in keyword and hybrid mode. Empty in semantic mode,
   *     where nothing matches word for word
   * @param score what the mode ranks by. Semantic mode: cosine similarity, {@code 1 - distance}
   *     (RET-2). Keyword mode: the {@code ts_rank_cd} rank, which is not a similarity and has no
   *     upper bound (RET-5). Hybrid mode: the fused score, the sum of {@code 1 / (60 + rank)} over
   *     both rankings, at most about 0.033 (RET-6)
   * @param similarity the cosine similarity to the query, in semantic and hybrid mode; null in
   *     keyword mode
   */
  public record SearchResult(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID documentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String title,
      @Schema(requiredMode = RequiredMode.REQUIRED) int pageStart,
      @Schema(requiredMode = RequiredMode.REQUIRED) int pageEnd,
      @Schema(requiredMode = RequiredMode.REQUIRED) String text,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<Highlight> highlights,
      @Schema(requiredMode = RequiredMode.REQUIRED) double score,
      @Schema(requiredMode = RequiredMode.NOT_REQUIRED, nullable = true) Double similarity) {}

  /**
   * A matched part of {@link SearchResult#text()}, as positions in UTF-16 code units, which is how
   * both Java and JavaScript index strings.
   *
   * @param start the first character
   * @param end one past the last character
   */
  public record Highlight(
      @Schema(requiredMode = RequiredMode.REQUIRED) int start,
      @Schema(requiredMode = RequiredMode.REQUIRED) int end) {}
}

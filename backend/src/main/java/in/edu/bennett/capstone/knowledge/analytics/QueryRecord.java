package in.edu.bennett.capstone.knowledge.analytics;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One {@code query_log} row: ids, times and numbers only. There is no field for question or query
 * text (D14, AUD-2).
 *
 * @param retrievalMillis embedding the query and the search statement; null when nothing was
 *     retrieved
 * @param generationMillis the model calls; null when no model was called
 * @param topSimilarity the best cosine similarity retrieved; null in keyword mode or when nothing
 *     was retrieved
 * @param costUsd the tokens at the configured prices; zero when no model was called
 */
public record QueryRecord(
    UUID userId,
    QueryType type,
    QueryOutcome outcome,
    long totalMillis,
    Long retrievalMillis,
    Long generationMillis,
    Integer retrievedCount,
    Double topSimilarity,
    String model,
    int modelCalls,
    Integer inputTokens,
    Long cachedInputTokens,
    Integer outputTokens,
    BigDecimal costUsd) {

  public QueryRecord {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(outcome, "outcome");
    costUsd = costUsd == null ? BigDecimal.ZERO : costUsd;
  }

  /** A search: no model call, so no tokens and no cost. */
  public static QueryRecord search(
      UUID userId,
      QueryType type,
      QueryOutcome outcome,
      long totalMillis,
      Long retrievalMillis,
      Integer retrievedCount,
      Double topSimilarity) {
    return new QueryRecord(
        userId,
        type,
        outcome,
        totalMillis,
        retrievalMillis,
        null,
        retrievedCount,
        topSimilarity,
        null,
        0,
        null,
        null,
        null,
        BigDecimal.ZERO);
  }
}

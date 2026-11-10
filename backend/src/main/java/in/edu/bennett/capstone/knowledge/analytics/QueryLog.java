package in.edu.bennett.capstone.knowledge.analytics;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Writes {@code query_log} (specification 7.2, 14.3, ADR-041): one short statement after a search,
 * question, summary run or comparison has its outcome, with no transaction held around a model call
 * (4.5). Analytics must never break the request it describes, so a failed write is logged and
 * swallowed.
 */
@Component
public class QueryLog {

  private static final Logger log = LoggerFactory.getLogger(QueryLog.class);

  private final JdbcClient jdbc;
  private final Clock clock;

  QueryLog(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public void record(QueryRecord entry) {
    try {
      jdbc.sql(
              """
              INSERT INTO query_log (created_at, user_id, type, outcome, total_ms, retrieval_ms,
                                     generation_ms, retrieved_count, top_similarity, model,
                                     model_calls, input_tokens, cached_input_tokens,
                                     output_tokens, cost_usd)
              VALUES (:createdAt, :userId, :type, :outcome, :totalMs, :retrievalMs,
                      :generationMs, :retrievedCount, :topSimilarity, :model,
                      :modelCalls, :inputTokens, :cachedInputTokens,
                      :outputTokens, :costUsd)
              """)
          .param("createdAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
          .param("userId", entry.userId())
          .param("type", entry.type().name())
          .param("outcome", entry.outcome().name())
          .param("totalMs", clamp(entry.totalMillis()))
          .param(
              "retrievalMs",
              entry.retrievalMillis() == null ? null : clamp(entry.retrievalMillis()))
          .param(
              "generationMs",
              entry.generationMillis() == null ? null : clamp(entry.generationMillis()))
          .param("retrievedCount", entry.retrievedCount())
          .param("topSimilarity", entry.topSimilarity())
          .param("model", entry.model())
          .param("modelCalls", entry.modelCalls())
          .param("inputTokens", entry.inputTokens())
          .param("cachedInputTokens", entry.cachedInputTokens())
          .param("outputTokens", entry.outputTokens())
          .param("costUsd", entry.costUsd())
          .update();
    } catch (RuntimeException e) {
      // Ids and numbers only (SEC-10).
      log.warn(
          "Could not write the query log row type={} outcome={} user={}",
          entry.type(),
          entry.outcome(),
          entry.userId(),
          e);
    }
  }

  private static int clamp(long millis) {
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, millis));
  }
}

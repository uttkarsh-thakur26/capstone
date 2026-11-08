package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The {@code document_summary} table (SUM-4, SUM-5), through {@link JdbcClient} like the audit log,
 * because its content is {@code jsonb}. Each method is one statement, in a transaction of its own:
 * none is held while a model is called (4.5). It never checks access; {@link SummaryService} does,
 * before it calls any method here (ACL-E5).
 */
@Component
class SummaryStore {

  /** One stored summary. */
  record Row(
      UUID id,
      UUID documentId,
      String model,
      String promptVersion,
      String status,
      String content,
      String failureMessage,
      Instant sourceIndexedAt,
      int modelCalls,
      Instant finishedAt) {}

  private final JdbcClient jdbc;
  private final Clock clock;

  SummaryStore(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  Optional<Row> find(UUID documentId, String model, String promptVersion) {
    return jdbc.sql(
            """
            SELECT id, document_id, model, prompt_version, status, content::text AS content,
                   failure_message, source_indexed_at, model_calls, finished_at
            FROM document_summary
            WHERE document_id = :documentId AND model = :model AND prompt_version = :version
            """)
        .param("documentId", documentId)
        .param("model", model)
        .param("version", promptVersion)
        .query(
            (row, rowNum) ->
                new Row(
                    row.getObject("id", UUID.class),
                    row.getObject("document_id", UUID.class),
                    row.getString("model"),
                    row.getString("prompt_version"),
                    row.getString("status"),
                    row.getString("content"),
                    row.getString("failure_message"),
                    row.getObject("source_indexed_at", OffsetDateTime.class).toInstant(),
                    row.getInt("model_calls"),
                    instant(row.getObject("finished_at", OffsetDateTime.class))))
        .optional();
  }

  /**
   * Starts a summary of the document as it is indexed now, unless one is already being made or a
   * finished one of the same indexing exists. One statement decides, so two requests at the same
   * moment start one run (SUM-5).
   *
   * @return the id of the row to fill when this call started a run; empty when another one is
   *     running or a fresh summary exists
   */
  Optional<UUID> start(
      UUID documentId,
      String model,
      String promptVersion,
      Instant sourceIndexedAt,
      UUID requestedBy) {
    Timestamp now = Timestamp.from(clock.instant());
    return jdbc.sql(
            """
            INSERT INTO document_summary (document_id, model, prompt_version, status,
                source_indexed_at, requested_by, started_at, created_at, updated_at)
            VALUES (:documentId, :model, :version, 'PENDING', :indexedAt, :requestedBy, :now,
                :now, :now)
            ON CONFLICT (document_id, model, prompt_version) DO UPDATE
               SET status = 'PENDING', content = NULL, failure_message = NULL,
                   source_indexed_at = EXCLUDED.source_indexed_at,
                   requested_by = EXCLUDED.requested_by, model_calls = 0, input_tokens = NULL,
                   cached_input_tokens = NULL, output_tokens = NULL, started_at = EXCLUDED.started_at,
                   finished_at = NULL, updated_at = EXCLUDED.updated_at
             WHERE document_summary.status = 'FAILED'
                OR (document_summary.status = 'READY'
                    AND document_summary.source_indexed_at <> EXCLUDED.source_indexed_at)
            RETURNING id
            """)
        .param("documentId", documentId)
        .param("model", model)
        .param("version", promptVersion)
        .param("indexedAt", Timestamp.from(sourceIndexedAt))
        .param("requestedBy", requestedBy)
        .param("now", now)
        .query(UUID.class)
        .optional();
  }

  /** Stores the finished summary; false when the row is gone or no longer being made. */
  boolean ready(UUID id, String content, int modelCalls, Usage usage) {
    return jdbc.sql(
                """
                UPDATE document_summary
                SET status = 'READY', content = CAST(:content AS jsonb), model_calls = :calls,
                    input_tokens = :input, cached_input_tokens = :cached, output_tokens = :output,
                    finished_at = :now, updated_at = :now
                WHERE id = :id AND status = 'PENDING'
                """)
            .param("id", id)
            .param("content", content)
            .param("calls", modelCalls)
            .param("input", usage == null ? null : usage.inputTokens())
            .param("cached", usage == null ? null : usage.cachedInputTokens())
            .param("output", usage == null ? null : usage.outputTokens())
            .param("now", Timestamp.from(clock.instant()))
            .update()
        == 1;
  }

  /** Marks the run failed, with a reason a reader can see; false when the row is gone. */
  boolean failed(UUID id, String message, int modelCalls, Usage usage) {
    return jdbc.sql(
                """
                UPDATE document_summary
                SET status = 'FAILED', failure_message = :message, model_calls = :calls,
                    input_tokens = :input, cached_input_tokens = :cached, output_tokens = :output,
                    finished_at = :now, updated_at = :now
                WHERE id = :id AND status = 'PENDING'
                """)
            .param("id", id)
            .param("message", message)
            .param("calls", modelCalls)
            .param("input", usage == null ? null : usage.inputTokens())
            .param("cached", usage == null ? null : usage.cachedInputTokens())
            .param("output", usage == null ? null : usage.outputTokens())
            .param("now", Timestamp.from(clock.instant()))
            .update()
        == 1;
  }

  /**
   * After a restart no run is alive, so every summary still marked as being made failed with the
   * restart. T1 runs one backend instance (ING-12), so no other instance can be making one.
   *
   * @return how many were marked
   */
  int failInterrupted() {
    return jdbc.sql(
            """
            UPDATE document_summary
            SET status = 'FAILED', failure_message = :message, finished_at = :now, updated_at = :now
            WHERE status = 'PENDING'
            """)
        .param("message", "The summary was interrupted by a restart. Try again.")
        .param("now", Timestamp.from(clock.instant()))
        .update();
  }

  private static Instant instant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }
}

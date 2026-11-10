package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.storage.FileStorage;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code document} table as the ingestion queue (ING-1, ING-2, ADR-034). The status machine
 * stays in this module: ingestion moves a document only through these methods.
 *
 * <p>A claim gives the run a new id and a heartbeat. Every later write of the run requires {@code
 * status = 'PROCESSING'} and that run id, and reports whether it matched, so a run that was
 * recovered, deleted or replaced cannot change the document. Times come from the injected {@link
 * Clock} (4.5).
 *
 * <p>These statements do not change {@code row_version}. They write only the processing columns,
 * and {@link Document} is {@code @DynamicUpdate}, so a concurrent edit through JPA writes only the
 * columns it changed and never puts back an old status.
 */
@Service
public class DocumentProcessing {

  private static final Logger log = LoggerFactory.getLogger(DocumentProcessing.class);

  private final JdbcClient jdbc;
  private final FileStorage storage;
  private final Clock clock;

  DocumentProcessing(JdbcClient jdbc, FileStorage storage, Clock clock) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.clock = clock;
  }

  /**
   * Claims the oldest queued document in one statement. {@code SKIP LOCKED} lets concurrent claims
   * pass over a row another claim is taking, so two workers never get the same document (ING-1).
   */
  public Optional<ProcessingRun> claimNext() {
    OffsetDateTime now = now();
    return jdbc.sql(
            """
            UPDATE document SET status = 'PROCESSING', processing_run_id = :run,
                processing_started_at = :now, processing_heartbeat_at = :now, updated_at = :now
            WHERE id = (
                SELECT id FROM document WHERE status = 'UPLOADED'
                ORDER BY updated_at, id LIMIT 1 FOR UPDATE SKIP LOCKED)
            RETURNING id, processing_run_id, storage_key, page_count, processing_started_at
            """)
        .param("run", UUID.randomUUID())
        .param("now", now)
        .query(DocumentProcessing::run)
        .optional();
  }

  /** Refreshes the heartbeat of the given runs and returns those that are still current. */
  public Set<UUID> heartbeat(Collection<UUID> runIds) {
    if (runIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql(
                """
                UPDATE document SET processing_heartbeat_at = :now
                WHERE status = 'PROCESSING' AND processing_run_id IN (:runs)
                RETURNING processing_run_id
                """)
            .param("now", now())
            .param("runs", runIds)
            .query(UUID.class)
            .list());
  }

  /**
   * Locks the document for the run's final write, inside the caller's transaction. False when the
   * run no longer holds it.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean lockRun(ProcessingRun run) {
    return jdbc.sql(
            """
            SELECT id FROM document
            WHERE id = :id AND status = 'PROCESSING' AND processing_run_id = :run FOR UPDATE
            """)
        .param("id", run.documentId())
        .param("run", run.runId())
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  /**
   * ING-3: marks the document {@code INDEXED} in the transaction that replaced its chunks. Throws
   * when the run no longer holds the document, so that transaction rolls back.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void markIndexed(
      ProcessingRun run, int pageCount, int chunkCount, String embeddingModel, int embeddingDim) {
    int updated =
        jdbc.sql(
                """
                UPDATE document SET status = 'INDEXED', page_count = :pages, chunk_count = :chunks,
                    embedding_model = :model, embedding_dim = :dim, indexed_at = :now,
                    failure_code = NULL, failure_message = NULL,
                    processing_run_id = NULL, processing_heartbeat_at = NULL, updated_at = :now
                WHERE id = :id AND status = 'PROCESSING' AND processing_run_id = :run
                """)
            .param("pages", pageCount)
            .param("chunks", chunkCount)
            .param("model", embeddingModel)
            .param("dim", embeddingDim)
            .param("now", now())
            .param("id", run.documentId())
            .param("run", run.runId())
            .update();
    if (updated != 1) {
      throw new IllegalStateException("Run " + run.runId() + " no longer holds its document");
    }
  }

  /**
   * Marks the document {@code FAILED} (8.3). {@code message} is shown to users instead of the
   * code's own message when it adds something, such as the actual page count; it never holds
   * internal details. Returns false when the run no longer holds the document.
   */
  public boolean markFailed(ProcessingRun run, FailureCode code, String message) {
    return jdbc.sql(
                """
                UPDATE document SET status = 'FAILED', failure_code = :code, failure_message = :message,
                    processing_run_id = NULL, processing_heartbeat_at = NULL, updated_at = :now
                WHERE id = :id AND status = 'PROCESSING' AND processing_run_id = :run
                """)
            .param("code", code.name())
            .param("message", message)
            .param("now", now())
            .param("id", run.documentId())
            .param("run", run.runId())
            .update()
        == 1;
  }

  /**
   * Gives an unfinished run back to the queue without counting an attempt, for a run stopped by a
   * shutdown (ADR-034). Returns false when the run no longer holds the document.
   */
  public boolean release(ProcessingRun run) {
    return jdbc.sql(
                """
                UPDATE document SET status = 'UPLOADED', processing_started_at = NULL,
                    processing_run_id = NULL, processing_heartbeat_at = NULL, updated_at = :now
                WHERE id = :id AND status = 'PROCESSING' AND processing_run_id = :run
                """)
            .param("now", now())
            .param("id", run.documentId())
            .param("run", run.runId())
            .update()
        == 1;
  }

  /**
   * ING-2: a run whose heartbeat is older than {@code stuckTimeout} belongs to a worker that died.
   * Its document goes back to {@code UPLOADED} with one more attempt counted, or becomes {@code
   * FAILED} with {@code PROCESSING_TIMEOUT} once {@code maxAttempts} is reached (ADR-030).
   *
   * @return how many documents were recovered
   */
  public int recoverStuck(Duration stuckTimeout, int maxAttempts) {
    OffsetDateTime now = now();
    List<String> recovered =
        jdbc.sql(
                """
                UPDATE document SET processing_attempts = processing_attempts + 1,
                    status = CASE WHEN processing_attempts + 1 >= :max THEN 'FAILED' ELSE 'UPLOADED' END,
                    failure_code = CASE WHEN processing_attempts + 1 >= :max
                        THEN 'PROCESSING_TIMEOUT' END,
                    processing_started_at = NULL, processing_run_id = NULL,
                    processing_heartbeat_at = NULL, updated_at = :now
                WHERE status = 'PROCESSING' AND processing_heartbeat_at < :cutoff
                RETURNING id || ' -> ' || status
                """)
            .param("max", maxAttempts)
            .param("now", now)
            .param("cutoff", now.minus(stuckTimeout))
            .query(String.class)
            .list();
    recovered.forEach(change -> log.warn("Recovered a stuck processing run: document {}", change));
    return recovered.size();
  }

  /**
   * S11, ADR-041: sends every {@code INDEXED} document whose vectors come from another model back
   * to {@code UPLOADED}, with the same columns reset as reprocess resets (DOC-10), so the worker
   * embeds it again with {@code model}. Until then it is not retrievable (ACL-4).
   *
   * @return how many documents were sent back
   */
  public int requeueOtherModels(String model) {
    return jdbc.sql(
            """
            UPDATE document SET status = 'UPLOADED', failure_code = NULL, failure_message = NULL,
                processing_attempts = 0, processing_started_at = NULL, processing_run_id = NULL,
                processing_heartbeat_at = NULL, indexed_at = NULL, updated_at = :now
            WHERE status = 'INDEXED' AND embedding_model IS DISTINCT FROM :model
            """)
        .param("now", now())
        .param("model", model)
        .update();
  }

  /** The models whose vectors the {@code INDEXED} documents hold (S11, ADR-034). */
  public List<String> indexedEmbeddingModels() {
    return jdbc.sql(
            """
            SELECT DISTINCT embedding_model FROM document
            WHERE status = 'INDEXED' AND embedding_model IS NOT NULL ORDER BY 1
            """)
        .query(String.class)
        .list();
  }

  /**
   * Opens the run's stored file. The caller closes the stream. No transaction is involved (4.5).
   */
  public InputStream openFile(ProcessingRun run) {
    return storage.open(run.storageKey());
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private static ProcessingRun run(ResultSet rs, int row) throws SQLException {
    return new ProcessingRun(
        rs.getObject("id", UUID.class),
        rs.getObject("processing_run_id", UUID.class),
        rs.getString("storage_key"),
        rs.getInt("page_count"),
        rs.getObject("processing_started_at", OffsetDateTime.class).toInstant());
  }
}

package in.edu.bennett.capstone.knowledge.storage;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes stored files without losing a failed deletion (DOC-9, ADR-031). The key is recorded in
 * {@code storage_deletion} by the transaction that removes the rows, so it is kept exactly when
 * that transaction commits. The file is deleted afterwards, outside any transaction (4.5), and the
 * record removed; what fails stays recorded and is retried every {@code app.storage
 * .cleanup-interval}, also after a restart.
 */
@Component
public class FileDeletionQueue {

  private static final Logger log = LoggerFactory.getLogger(FileDeletionQueue.class);

  /** Rows retried per run, oldest first. The rest wait for the next run. */
  private static final int RETRY_BATCH = 100;

  private final JdbcClient jdbc;
  private final FileStorage storage;
  private final Clock clock;

  FileDeletionQueue(JdbcClient jdbc, FileStorage storage, Clock clock) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.clock = clock;
  }

  /**
   * Records that the file must go. Joins the caller's transaction, which must exist, so the record
   * is committed or rolled back together with the rows that pointed at the file.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(String key) {
    insert(key, 0);
  }

  /**
   * Deletes a file whose deletion was recorded by {@link #enqueue}, once that transaction has
   * committed. Must be called outside a transaction.
   */
  public void deleteEnqueued(String key) {
    attempt(key);
  }

  /**
   * Deletes a file that no row points at, for example after a failed insert. If that fails, the
   * key is recorded so the cleanup task retries it.
   */
  public void deleteOrRetryLater(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      insert(key, 1);
    }
  }

  /** The cleanup task: tries every recorded deletion again, oldest first. */
  public void retryPending() {
    List<String> keys =
        jdbc.sql(
                "SELECT storage_key FROM storage_deletion ORDER BY requested_at, id LIMIT :limit")
            .param("limit", RETRY_BATCH)
            .query(String.class)
            .list();
    keys.forEach(this::attempt);
  }

  private void attempt(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      // The storage logged the cause; the record stays for the next run.
      jdbc.sql(
              """
              UPDATE storage_deletion SET attempts = attempts + 1, last_attempt_at = :now
              WHERE storage_key = :key
              """)
          .param("now", now())
          .param("key", key)
          .update();
      return;
    }
    jdbc.sql("DELETE FROM storage_deletion WHERE storage_key = :key").param("key", key).update();
    log.info("Deleted stored file {}", key);
  }

  private void insert(String key, int attempts) {
    jdbc.sql(
            """
            INSERT INTO storage_deletion (storage_key, requested_at, attempts, last_attempt_at)
            VALUES (:key, :now, :attempts, :lastAttempt)
            ON CONFLICT (storage_key) DO NOTHING
            """)
        .param("key", key)
        .param("now", now())
        .param("attempts", attempts)
        .param("lastAttempt", attempts == 0 ? null : now())
        .update();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}

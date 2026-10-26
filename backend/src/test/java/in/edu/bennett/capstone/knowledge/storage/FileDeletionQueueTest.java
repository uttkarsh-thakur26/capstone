package in.edu.bennett.capstone.knowledge.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** DOC-9: a failed file deletion is recorded and retried, also after a restart (ADR-031). */
@IntegrationTest
class FileDeletionQueueTest {

  @Autowired JdbcClient jdbc;

  @Autowired Clock clock;

  @Autowired TransactionTemplate transactions;

  @Autowired FileDeletionQueue queueBean;

  @TempDir Path root;

  private SwitchableStorage storage;
  private FileDeletionQueue queue;

  @BeforeEach
  void createQueue() {
    storage = new SwitchableStorage(new LocalFileStorage(root));
    queue = new FileDeletionQueue(jdbc, storage, clock);
  }

  @Test
  void theDeletionIsRecordedOnlyIfTheTransactionCommits() {
    String key = storage.store(new ByteArrayInputStream(new byte[] {1}));

    transactions.executeWithoutResult(
        status -> {
          queue.enqueue(key);
          status.setRollbackOnly();
        });

    assertThat(pending(key)).isNull();
    assertThat(root.resolve(key)).exists();
  }

  @Test
  void afterCommitTheFileAndTheRecordAreRemoved() {
    String key = storage.store(new ByteArrayInputStream(new byte[] {1}));

    transactions.executeWithoutResult(status -> queue.enqueue(key));
    assertThat(pending(key)).isZero();
    queue.deleteEnqueued(key);

    assertThat(root.resolve(key)).doesNotExist();
    assertThat(pending(key)).isNull();
  }

  @Test
  void aFailedDeletionStaysRecordedAndTheCleanupTaskRetriesIt() {
    String key = storage.store(new ByteArrayInputStream(new byte[] {1}));
    transactions.executeWithoutResult(status -> queue.enqueue(key));

    storage.failing = true;
    queue.deleteEnqueued(key);
    assertThat(root.resolve(key)).exists();
    assertThat(pending(key)).isEqualTo(1);

    storage.failing = false;
    queue.retryPending();
    assertThat(root.resolve(key)).doesNotExist();
    assertThat(pending(key)).isNull();
  }

  @Test
  void anUnreferencedFileThatCannotBeDeletedIsRecordedForRetry() {
    String key = storage.store(new ByteArrayInputStream(new byte[] {1}));

    storage.failing = true;
    queue.deleteOrRetryLater(key);
    assertThat(pending(key)).isEqualTo(1);

    storage.failing = false;
    queue.retryPending();
    assertThat(root.resolve(key)).doesNotExist();
    assertThat(pending(key)).isNull();
  }

  @Test
  void enqueueingOutsideATransactionIsRefused() {
    assertThatThrownBy(() -> queueBean.enqueue("00000000-0000-0000-0000-000000000000"))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /** The number of failed attempts recorded for the key, or null when nothing is recorded. */
  private Integer pending(String key) {
    return jdbc.sql("SELECT attempts FROM storage_deletion WHERE storage_key = :key")
        .param("key", key)
        .query(Integer.class)
        .optional()
        .orElse(null);
  }

  /** A real storage whose deletions can be made to fail, as when the volume is unavailable. */
  private static final class SwitchableStorage implements FileStorage {

    private final FileStorage delegate;
    boolean failing;

    SwitchableStorage(FileStorage delegate) {
      this.delegate = delegate;
    }

    @Override
    public String store(InputStream content) {
      return delegate.store(content);
    }

    @Override
    public InputStream open(String key) {
      return delegate.open(key);
    }

    @Override
    public void delete(String key) {
      if (failing) {
        throw new StorageUnavailableException();
      }
      delegate.delete(key);
    }
  }
}

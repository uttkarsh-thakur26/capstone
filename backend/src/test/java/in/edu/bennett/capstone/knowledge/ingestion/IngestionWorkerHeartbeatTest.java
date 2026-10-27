package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.edu.bennett.capstone.knowledge.ai.EmbeddingModelCheck;
import in.edu.bennett.capstone.knowledge.document.DocumentProcessing;
import in.edu.bennett.capstone.knowledge.document.ProcessingRun;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Independent review finding (Phase 3): a run claimed while a heartbeat is in flight is not in the
 * heartbeat's list of runs, and must not be taken for a run that lost its lease.
 */
class IngestionWorkerHeartbeatTest {

  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
  private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  private final CountDownLatch finish = new CountDownLatch(1);
  private final AtomicReference<RunControl> started = new AtomicReference<>();
  private final CountDownLatch running = new CountDownLatch(1);

  @AfterEach
  void stop() {
    finish.countDown();
    scheduler.shutdown();
    executor.shutdown();
  }

  @Test
  void aRunClaimedDuringAHeartbeatKeepsRunning() throws InterruptedException {
    scheduler.initialize();
    executor.setCorePoolSize(2);
    executor.initialize();
    DocumentProcessing processing = mock(DocumentProcessing.class);
    EmbeddingModelCheck verified = mock(EmbeddingModelCheck.class);
    when(verified.ensureVerified()).thenReturn(true);
    when(processing.claimNext()).thenReturn(Optional.empty());
    IngestionWorker worker =
        new IngestionWorker(
            processing,
            new StubPipeline(),
            verified,
            properties(),
            scheduler,
            executor,
            Clock.systemUTC());
    worker.start();
    // While the heartbeat's update runs, the other scheduler thread claims a new document.
    when(processing.heartbeat(any()))
        .thenAnswer(
            call -> {
              Set<UUID> sent = new HashSet<>(call.getArgument(0));
              when(processing.claimNext())
                  .thenReturn(
                      Optional.of(
                          new ProcessingRun(
                              UUID.randomUUID(), UUID.randomUUID(), "key", 1, Instant.now())))
                  .thenReturn(Optional.empty());
              worker.poll();
              return sent;
            });

    worker.heartbeat();

    // The run starts on an executor thread; wait until it has.
    assertThat(running.await(5, TimeUnit.SECONDS))
        .as("the run claimed during the heartbeat")
        .isTrue();
    assertThat(started.get().stopRequested()).isFalse();
  }

  private static IngestionProperties properties() {
    return new IngestionProperties(
        true,
        400,
        60,
        16,
        2,
        Duration.ofHours(1),
        Duration.ofHours(1),
        Duration.ofHours(1),
        Duration.ofHours(4),
        3,
        500,
        5000,
        20,
        Duration.ofMillis(100));
  }

  private final class StubPipeline extends IngestionPipeline {

    StubPipeline() {
      super(null, null, null, null, null, null, null, null, null);
    }

    @Override
    Outcome process(ProcessingRun run, RunControl control) {
      started.set(control);
      running.countDown();
      try {
        finish.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return Outcome.INDEXED;
    }
  }
}

package in.edu.bennett.capstone.knowledge.ingestion;

import in.edu.bennett.capstone.knowledge.ai.EmbeddingModelCheck;
import in.edu.bennett.capstone.knowledge.document.DocumentProcessing;
import in.edu.bennett.capstone.knowledge.document.DocumentQueued;
import in.edu.bennett.capstone.knowledge.document.ProcessingRun;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The ingestion worker (ING-1, ING-2, ING-12, ADR-034). It starts after the application context is
 * ready and stops before it closes.
 *
 * <ul>
 *   <li>Polls for queued documents every {@code poll-interval}, and right after an upload or
 *       reprocess commits. It claims only as many documents as it has free threads.
 *   <li>Every {@code heartbeat-interval} it refreshes the heartbeat of its running runs. A run the
 *       database no longer recognises lost its lease and stops at its next checkpoint.
 *   <li>Every {@code recovery-interval}, and at start, it returns runs with an old heartbeat to the
 *       queue: their worker died.
 *   <li>On stop it claims nothing more, tells running runs to give their documents back, and waits
 *       up to {@code shutdown-wait}.
 * </ul>
 *
 * The parallel limit is per instance; T1 runs one backend.
 */
@Component
class IngestionWorker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

  private final DocumentProcessing processing;
  private final IngestionPipeline pipeline;
  private final EmbeddingModelCheck modelCheck;
  private final IngestionProperties properties;
  private final TaskScheduler scheduler;
  private final ThreadPoolTaskExecutor executor;
  private final Clock clock;
  private final Semaphore slots;
  private final Map<UUID, RunControl> active = new ConcurrentHashMap<>();
  private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
  private volatile boolean running;

  IngestionWorker(
      DocumentProcessing processing,
      IngestionPipeline pipeline,
      EmbeddingModelCheck modelCheck,
      IngestionProperties properties,
      TaskScheduler scheduler,
      ThreadPoolTaskExecutor ingestionExecutor,
      Clock clock) {
    this.processing = processing;
    this.pipeline = pipeline;
    this.modelCheck = modelCheck;
    this.properties = properties;
    this.scheduler = scheduler;
    this.executor = ingestionExecutor;
    this.clock = clock;
    this.slots = new Semaphore(properties.parallelDocuments());
  }

  @Override
  public synchronized void start() {
    if (!properties.enabled() || running) {
      return;
    }
    running = true;
    tasks.add(scheduler.scheduleWithFixedDelay(this::recover, properties.recoveryInterval()));
    tasks.add(scheduler.scheduleAtFixedRate(this::heartbeat, properties.heartbeatInterval()));
    tasks.add(scheduler.scheduleWithFixedDelay(this::poll, properties.pollInterval()));
    log.info(
        "Ingestion worker started: {} documents at a time, polling every {}",
        properties.parallelDocuments(),
        properties.pollInterval());
  }

  @Override
  public void stop() {
    synchronized (this) {
      if (!running) {
        return;
      }
      running = false;
      tasks.forEach(task -> task.cancel(false));
      tasks.clear();
    }
    active.values().forEach(RunControl::shutDown);
    try {
      if (slots.tryAcquire(
          properties.parallelDocuments(),
          properties.shutdownWait().toMillis(),
          TimeUnit.MILLISECONDS)) {
        slots.release(properties.parallelDocuments());
      } else {
        log.warn("Ingestion runs still busy at shutdown; recovery returns their documents later");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /**
   * Wakes the worker once an upload or reprocess has committed. It must never throw: an exception
   * here would reach the request after its transaction committed, and the upload would then remove
   * the file of a document that exists.
   */
  @TransactionalEventListener
  void onQueued(DocumentQueued event) {
    wake();
  }

  /** Asks for a poll on the scheduler thread. Never throws. */
  private void wake() {
    try {
      if (running) {
        scheduler.schedule(this::poll, clock.instant());
      }
    } catch (RuntimeException e) {
      log.warn("Could not wake the ingestion worker: {}", e.toString());
    }
  }

  /** Claims queued documents while threads are free and hands each to the pipeline. */
  synchronized void poll() {
    try {
      if (!running || !modelCheck.ensureVerified()) {
        return;
      }
      while (running && slots.tryAcquire()) {
        Optional<ProcessingRun> claimed;
        try {
          claimed = processing.claimNext();
        } catch (RuntimeException e) {
          slots.release();
          throw e;
        }
        if (claimed.isEmpty()) {
          slots.release();
          return;
        }
        start(claimed.get());
      }
    } catch (RuntimeException e) {
      log.warn("Polling for queued documents failed: {}", e.toString());
    }
  }

  private void start(ProcessingRun run) {
    RunControl control = RunControl.running();
    active.put(run.runId(), control);
    try {
      executor.execute(
          () -> {
            try {
              pipeline.process(run, control);
            } finally {
              active.remove(run.runId());
              slots.release();
              // A freed thread takes the next queued document now, not at the next timed poll.
              wake();
            }
          });
    } catch (RuntimeException e) {
      active.remove(run.runId());
      slots.release();
      processing.release(run);
      throw e;
    }
  }

  /**
   * Refreshes the runs' heartbeats; a run the database no longer recognises stops. Only the runs
   * that were sent are judged: one claimed by the other scheduler thread meanwhile is not in the
   * answer, yet holds its lease.
   */
  void heartbeat() {
    try {
      Set<UUID> sent = Set.copyOf(active.keySet());
      Set<UUID> current = processing.heartbeat(sent);
      for (UUID runId : sent) {
        RunControl control = active.get(runId);
        if (control != null && !current.contains(runId)) {
          control.lose();
        }
      }
    } catch (RuntimeException e) {
      log.warn("Heartbeat of running documents failed: {}", e.toString());
    }
  }

  void recover() {
    try {
      processing.recoverStuck(properties.stuckTimeout(), properties.maxAttempts());
    } catch (RuntimeException e) {
      log.warn("Recovery of stuck documents failed: {}", e.toString());
    }
  }
}

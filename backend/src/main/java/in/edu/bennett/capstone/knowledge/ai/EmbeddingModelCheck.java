package in.edu.bennett.capstone.knowledge.ai;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * ING-9: the model's real vector length must equal {@code app.embedding.dimensions}, or vectors
 * would not fit the column and searches would compare unlike things.
 *
 * <p>Checked once at startup. A model that answers with another length stops the application. A
 * model that does not answer (Ollama not started yet, or not reachable from CI) does not: the
 * application starts, nothing is embedded, and the ingestion worker asks again before each poll
 * until the check passes (ADR-034). A mismatch found that way pauses the worker and is logged.
 */
@Component
public class EmbeddingModelCheck implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingModelCheck.class);

  private final Embedder embedder;
  private volatile boolean verified;
  private volatile boolean started;
  private boolean mismatch;
  private String lastProblem;

  EmbeddingModelCheck(Embedder embedder) {
    this.embedder = embedder;
  }

  @Override
  public void afterSingletonsInstantiated() {
    ensureVerified();
    started = true;
  }

  /**
   * Whether vectors from the model may be stored. Asks the model again while it has not answered.
   *
   * @throws IllegalStateException at startup, when the model answers with the wrong dimension
   */
  public synchronized boolean ensureVerified() {
    if (verified) {
      return true;
    }
    if (mismatch) {
      return false;
    }
    int actual;
    try {
      actual = embedder.probeDimensions();
    } catch (RuntimeException e) {
      report(
          "The embedding model "
              + embedder.modelName()
              + " did not answer ("
              + e.getMessage()
              + "); documents wait until it does");
      return false;
    }
    if (actual != embedder.dimensions()) {
      String problem =
          "The embedding model "
              + embedder.modelName()
              + " returns vectors of "
              + actual
              + " dimensions, but app.embedding.dimensions is "
              + embedder.dimensions()
              + " (ING-9)";
      if (!started) {
        throw new IllegalStateException(problem);
      }
      if (!mismatch) {
        log.error(
            "{}; nothing is indexed until this is fixed and the application restarted", problem);
      }
      mismatch = true;
      return false;
    }
    verified = true;
    log.info(
        "Embedding model {} answers with {} dimensions",
        embedder.modelName(),
        embedder.dimensions());
    return true;
  }

  /** Logs a problem when it is new, so a paused worker does not repeat it every poll. */
  private void report(String problem) {
    if (!Objects.equals(problem, lastProblem)) {
      log.warn(problem);
      lastProblem = problem;
    }
  }
}

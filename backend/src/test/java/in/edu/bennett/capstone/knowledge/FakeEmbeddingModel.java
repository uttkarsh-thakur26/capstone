package in.edu.bennett.capstone.knowledge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The deterministic embedding model of specification 19.1: word tokens hashed into the configured
 * number of dimensions and normalized, so texts that share words are close and every run gives the
 * same vectors. Tests can make it fail, pause it in the middle of a run, or answer with another
 * dimension, and it records what it was asked and whether a transaction was open (4.5).
 *
 * <p>One instance serves every test of a Spring context: tests that change its behavior call {@link
 * #reset()} afterwards.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

  private final int configuredDimensions;
  private volatile int dimensions;
  private final Deque<Supplier<RuntimeException>> failures = new ArrayDeque<>();
  private final List<List<String>> requests = new CopyOnWriteArrayList<>();
  private final List<EmbeddingOptions> options = new CopyOnWriteArrayList<>();
  private final AtomicInteger callsInTransaction = new AtomicInteger();
  private final AtomicInteger concurrent = new AtomicInteger();
  private final AtomicInteger maxConcurrent = new AtomicInteger();
  private volatile CountDownLatch paused;
  private volatile CountDownLatch reachedPause;
  private volatile int pauseAtCall = -1;
  private final AtomicInteger calls = new AtomicInteger();
  private volatile int failAtCall = -1;
  private volatile Supplier<RuntimeException> failAtCallWith;
  private volatile long delayMillis;

  public FakeEmbeddingModel(int dimensions) {
    this.configuredDimensions = dimensions;
    this.dimensions = dimensions;
  }

  /** The vector this model gives a text, for fixtures that insert chunks directly. */
  public static float[] vectorOf(String text, int dimensions) {
    float[] vector = new float[dimensions];
    Matcher words = WORD.matcher(text.toLowerCase(Locale.ROOT));
    boolean any = false;
    while (words.find()) {
      int hash = words.group().hashCode();
      vector[Math.floorMod(hash, dimensions)] += (hash & 0x40000000) == 0 ? 1 : -1;
      any = true;
    }
    if (!any) {
      vector[0] = 1;
    }
    double norm = 0;
    for (float value : vector) {
      norm += value * value;
    }
    norm = Math.sqrt(norm);
    if (norm == 0) {
      vector[0] = 1;
      norm = 1;
    }
    for (int i = 0; i < dimensions; i++) {
      vector[i] = (float) (vector[i] / norm);
    }
    return vector;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    int call = calls.getAndIncrement();
    int now = concurrent.incrementAndGet();
    maxConcurrent.accumulateAndGet(now, Math::max);
    try {
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        callsInTransaction.incrementAndGet();
      }
      requests.add(List.copyOf(request.getInstructions()));
      options.add(request.getOptions());
      if (call == pauseAtCall) {
        reachedPause.countDown();
        await(paused);
      }
      if (delayMillis > 0) {
        sleep(delayMillis);
      }
      Supplier<RuntimeException> failure;
      synchronized (failures) {
        failure = failures.poll();
      }
      if (call == failAtCall) {
        failure = failAtCallWith;
      }
      if (failure != null) {
        throw failure.get();
      }
      List<Embedding> results = new ArrayList<>();
      List<String> texts = request.getInstructions();
      for (int i = 0; i < texts.size(); i++) {
        results.add(new Embedding(vectorOf(texts.get(i), dimensions), i));
      }
      return new EmbeddingResponse(results);
    } finally {
      concurrent.decrementAndGet();
    }
  }

  @Override
  public float[] embed(Document document) {
    return vectorOf(document.getText(), dimensions);
  }

  /** The next calls throw these, in order, then the model works again. */
  public void failNext(Supplier<RuntimeException> failure, int times) {
    synchronized (failures) {
      for (int i = 0; i < times; i++) {
        failures.add(failure);
      }
    }
  }

  /** The call with this number (counted from now, from 0) throws; the others work. */
  public void failAtCall(int callFromNow, Supplier<RuntimeException> failure) {
    failAtCallWith = failure;
    failAtCall = calls.get() + callFromNow;
  }

  /** Every call takes at least this long, so concurrent calls overlap. */
  public void delayEachCall(long millis) {
    delayMillis = millis;
  }

  /** From now on, answers with vectors of another length. */
  public void answerWithDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  /**
   * Makes the call with this number (counted from now, from 0) wait until {@link #resume()}.
   *
   * @return a latch that opens when that call has started
   */
  public CountDownLatch pauseAtCall(int callFromNow) {
    paused = new CountDownLatch(1);
    reachedPause = new CountDownLatch(1);
    pauseAtCall = calls.get() + callFromNow;
    return reachedPause;
  }

  public void resume() {
    if (paused != null) {
      paused.countDown();
    }
  }

  public List<List<String>> requests() {
    return requests;
  }

  public List<EmbeddingOptions> options() {
    return options;
  }

  public int callsInTransaction() {
    return callsInTransaction.get();
  }

  public int maxConcurrentCalls() {
    return maxConcurrent.get();
  }

  public void reset() {
    resume();
    pauseAtCall = -1;
    failAtCall = -1;
    delayMillis = 0;
    dimensions = configuredDimensions;
    synchronized (failures) {
      failures.clear();
    }
    requests.clear();
    options.clear();
    callsInTransaction.set(0);
    maxConcurrent.set(0);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("A paused embedding call was never resumed");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}

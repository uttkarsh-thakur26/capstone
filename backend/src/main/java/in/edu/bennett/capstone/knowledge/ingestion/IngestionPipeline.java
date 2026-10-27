package in.edu.bennett.capstone.knowledge.ingestion;

import in.edu.bennett.capstone.knowledge.ai.Embedder;
import in.edu.bennett.capstone.knowledge.ai.EmbeddingFailedException;
import in.edu.bennett.capstone.knowledge.document.DocumentProcessing;
import in.edu.bennett.capstone.knowledge.document.FailureCode;
import in.edu.bennett.capstone.knowledge.document.ProcessingRun;
import in.edu.bennett.capstone.knowledge.ingestion.ChunkWriter.EmbeddedChunk;
import in.edu.bennett.capstone.knowledge.ingestion.Chunker.Chunk;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One processing run (section 9.1): extract, normalize, chunk and embed with no transaction or
 * connection held (4.5), then replace the chunks and set {@code INDEXED} in one short transaction
 * (ING-3). Every way out ends the run: {@code INDEXED}, {@code FAILED} with a code (8.3), given
 * back to the queue when the application stops, or discarded when the run lost its lease.
 */
@Component
class IngestionPipeline {

  private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

  /** How a run ended. */
  enum Outcome {
    INDEXED,
    FAILED,
    RELEASED,
    DISCARDED
  }

  private final DocumentProcessing processing;
  private final TextExtractor extractor;
  private final TextNormalizer normalizer;
  private final Chunker chunker;
  private final Embedder embedder;
  private final ChunkWriter writer;
  private final IngestionProperties properties;
  private final TransactionTemplate transactions;
  private final MeterRegistry meters;

  IngestionPipeline(
      DocumentProcessing processing,
      TextExtractor extractor,
      TextNormalizer normalizer,
      Chunker chunker,
      Embedder embedder,
      ChunkWriter writer,
      IngestionProperties properties,
      PlatformTransactionManager transactionManager,
      MeterRegistry meters) {
    this.processing = processing;
    this.extractor = extractor;
    this.normalizer = normalizer;
    this.chunker = chunker;
    this.embedder = embedder;
    this.writer = writer;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
    this.meters = meters;
  }

  Outcome process(ProcessingRun run, RunControl control) {
    Path file = null;
    try {
      file = copyToTemp(run);
      Path stored = file;
      long started = System.nanoTime();
      TextExtractor.Extracted extracted = timed("extract", () -> extractor.extract(stored));
      control.checkpoint();
      List<Chunk> chunks =
          timed("chunk", () -> chunker.split(normalizer.normalize(extracted.pages())));
      if (chunks.isEmpty()) {
        throw new IngestionFailure(FailureCode.NO_TEXT_LAYER, null, "no chunks");
      }
      if (chunks.size() > properties.maxChunks()) {
        throw new IngestionFailure(
            FailureCode.TOO_MANY_CHUNKS,
            null,
            chunks.size() + " chunks, limit " + properties.maxChunks());
      }
      control.checkpoint();
      List<EmbeddedChunk> embedded = timed("embed", () -> embed(chunks, control));
      control.checkpoint();
      boolean indexed =
          timed(
              "index",
              () ->
                  transactions.execute(
                      status -> {
                        if (!processing.lockRun(run)) {
                          return false;
                        }
                        writer.replace(run.documentId(), embedded);
                        processing.markIndexed(
                            run,
                            extracted.pageCount(),
                            embedded.size(),
                            embedder.modelName(),
                            embedder.dimensions());
                        return true;
                      }));
      if (!indexed) {
        return discarded(run);
      }
      log.info(
          "Indexed document {}: {} pages, {} chunks in {} ms",
          run.documentId(),
          extracted.pageCount(),
          embedded.size(),
          Duration.ofNanos(System.nanoTime() - started).toMillis());
      return Outcome.INDEXED;
    } catch (RunControl.Stopped stopped) {
      if (stopped.reason() == RunControl.Stop.LOST) {
        return discarded(run);
      }
      return release(run);
    } catch (IngestionFailure failure) {
      return fail(run, failure.code(), failure.userMessage(), failure.getMessage());
    } catch (EmbeddingFailedException e) {
      if (control.stopRequested()) {
        return release(run);
      }
      return fail(
          run,
          FailureCode.EMBEDDING_ERROR,
          null,
          e.getCause() == null ? e.getMessage() : e.getCause().toString());
    } catch (RuntimeException e) {
      if (control.stopRequested()) {
        return release(run);
      }
      log.error("Processing document {} failed unexpectedly", run.documentId(), e);
      return fail(run, FailureCode.INTERNAL, null, e.toString());
    } catch (Error e) {
      log.error("Processing document {} failed with an error", run.documentId(), e);
      fail(run, FailureCode.INTERNAL, null, e.toString());
      throw e;
    } finally {
      deleteQuietly(file);
    }
  }

  private List<EmbeddedChunk> embed(List<Chunk> chunks, RunControl control) {
    List<EmbeddedChunk> embedded = new ArrayList<>(chunks.size());
    for (int from = 0; from < chunks.size(); from += properties.embeddingBatchSize()) {
      control.checkpoint();
      List<Chunk> batch =
          chunks.subList(from, Math.min(chunks.size(), from + properties.embeddingBatchSize()));
      List<float[]> vectors = embedder.embedDocuments(batch.stream().map(Chunk::content).toList());
      for (int i = 0; i < batch.size(); i++) {
        embedded.add(new EmbeddedChunk(batch.get(i), vectors.get(i)));
      }
    }
    return embedded;
  }

  private Path copyToTemp(ProcessingRun run) {
    Path temp;
    try {
      temp = Files.createTempFile("ingest-", ".pdf");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    try (InputStream content = processing.openFile(run)) {
      Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
      return temp;
    } catch (IOException | RuntimeException e) {
      deleteQuietly(temp);
      throw new IngestionFailure(
          FailureCode.EXTRACTION_ERROR, "The stored file could not be read.", e.toString(), e);
    }
  }

  private Outcome fail(ProcessingRun run, FailureCode code, String userMessage, String detail) {
    if (!processing.markFailed(run, code, userMessage)) {
      return discarded(run);
    }
    log.warn("Document {} failed with {}: {}", run.documentId(), code, detail);
    return Outcome.FAILED;
  }

  private Outcome release(ProcessingRun run) {
    try {
      if (processing.release(run)) {
        log.info(
            "Document {} goes back to the queue: the application is stopping", run.documentId());
      }
    } catch (RuntimeException e) {
      // The database is going away too; recovery returns the document once its heartbeat is old.
      log.warn("Could not give document {} back to the queue: {}", run.documentId(), e.toString());
    }
    return Outcome.RELEASED;
  }

  private static Outcome discarded(ProcessingRun run) {
    log.info(
        "Run {} of document {} no longer holds it (recovered, deleted or replaced); its work is"
            + " discarded",
        run.runId(),
        run.documentId());
    return Outcome.DISCARDED;
  }

  /** ING-11 and section 21: each stage's duration is logged and kept as a timer. */
  private <T> T timed(String stage, Supplier<T> work) {
    long start = System.nanoTime();
    try {
      return work.get();
    } finally {
      Duration took = Duration.ofNanos(System.nanoTime() - start);
      Timer.builder("ingestion.stage").tag("stage", stage).register(meters).record(took);
      log.info("Ingestion stage {} took {} ms", stage, took.toMillis());
    }
  }

  private static void deleteQuietly(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not remove the temporary file {}", file, e);
    }
  }
}

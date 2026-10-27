package in.edu.bennett.capstone.knowledge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.document.ProcessingRun;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import in.edu.bennett.capstone.knowledge.ingestion.IngestionPipeline.Outcome;
import in.edu.bennett.capstone.knowledge.ingestion.IngestionTestSupport.StoredChunk;
import in.edu.bennett.capstone.knowledge.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** The "Ingestion" tests of specification 19.2, and the ways a run can end (section 9, ADR-034). */
class IngestionPipelineTest extends IngestionTestSupport {

  private static final Pattern MARKER = Pattern.compile("page(\\d)marker");

  @Autowired FileStorage storage;

  @Autowired DataSource dataSource;

  /**
   * 19.2: page numbers of chunks match the fixture. Page 3 has no text, so a page number taken from
   * a position in the list instead of the PDF would be wrong from page 4 on; the text runs on
   * across pages, so some chunks span two of them.
   */
  @Test
  void pageNumbersOfChunksMatchTheFixture() {
    UUID id =
        upload(
            TestPdfs.withPageLines(
                List.of(
                    lines("page1marker", 22),
                    lines("page2marker", 22),
                    List.of(),
                    lines("page4marker", 22),
                    lines("page5marker", 22))));

    assertThat(processNext()).isEqualTo(Outcome.INDEXED);

    List<StoredChunk> chunks = chunks(id);
    assertThat(chunks).hasSizeGreaterThan(2);
    for (StoredChunk chunk : chunks) {
      Set<Integer> markerPages = new HashSet<>();
      Matcher marker = MARKER.matcher(chunk.content());
      while (marker.find()) {
        markerPages.add(Integer.parseInt(marker.group(1)));
      }
      assertThat(markerPages).as("pages of chunk %d", chunk.index()).isNotEmpty();
      assertThat(chunk.pageStart()).isEqualTo(markerPages.stream().min(Integer::compare).get());
      assertThat(chunk.pageEnd()).isEqualTo(markerPages.stream().max(Integer::compare).get());
      assertThat(chunk.pageStart()).isNotEqualTo(3);
      assertThat(chunk.tokens()).isPositive().isLessThanOrEqualTo(400);
    }
    assertThat(chunks).anyMatch(chunk -> chunk.pageEnd() > chunk.pageStart());
    assertThat(chunks).extracting(StoredChunk::index).containsExactly(range(chunks.size()));
    Map<String, Object> document = document(id);
    assertThat(document)
        .containsEntry("status", "INDEXED")
        .containsEntry("page_count", 5)
        .containsEntry("chunk_count", chunks.size())
        .containsEntry("embedding_model", "qwen3-embedding:0.6b")
        .containsEntry("embedding_dim", 1024)
        .containsEntry("processing_run_id", null);
    // ING-8: documents are embedded without the query prefix, and with the provider's options.
    assertThat(model.requests())
        .allSatisfy(texts -> assertThat(texts).noneMatch(t -> t.startsWith("Instruct:")));
    assertThat(model.options())
        .allMatch(options -> "qwen3-embedding:0.6b".equals(options.getModel()));
    // 4.5: no model call ran inside a transaction.
    assertThat(model.callsInTransaction()).isZero();
  }

  /** 19.2: deleting an indexed document removes its chunks (DATA-2). */
  @Test
  void deletingAnIndexedDocumentRemovesItsChunks() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 10))));
    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    assertThat(chunks(id)).isNotEmpty();

    assertThat(TestDocuments.send(mvc, faculty, HttpMethod.DELETE, "/api/v1/documents/" + id, null))
        .hasStatus2xxSuccessful();

    assertThat(chunks(id)).isEmpty();
  }

  /** 19.2: an encrypted file gives ENCRYPTED. Upload refuses one, so it is put into storage. */
  @Test
  void anEncryptedStoredFileFailsWithEncrypted() {
    UUID id = upload(TestPdfs.unique());
    replaceStoredFile(id, TestPdfs.encrypted("secret"));

    assertThat(processNext()).isEqualTo(Outcome.FAILED);

    assertThat(document(id))
        .containsEntry("status", "FAILED")
        .containsEntry("failure_code", "ENCRYPTED");
    assertThat(model.requests()).isEmpty();
  }

  /** 19.2: an image-only file gives NO_TEXT_LAYER. */
  @Test
  void anImageOnlyFileFailsWithNoTextLayer() {
    UUID id = upload(TestPdfs.imageOnly(2));

    assertThat(processNext()).isEqualTo(Outcome.FAILED);

    assertThat(document(id)).containsEntry("failure_code", "NO_TEXT_LAYER");
    assertThat(chunks(id)).isEmpty();
  }

  /** 19.2: an embedding failure gives FAILED, and reprocess then succeeds. */
  @Test
  void anEmbeddingFailureFailsTheDocumentAndReprocessThenSucceeds() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 10))));
    model.failNext(() -> new NonTransientAiException("400 bad request"), 1);

    assertThat(processNext()).isEqualTo(Outcome.FAILED);
    assertThat(document(id))
        .containsEntry("status", "FAILED")
        .containsEntry("failure_code", "EMBEDDING_ERROR");
    // A client error is not transient: one call, no retries.
    assertThat(model.requests()).hasSize(1);

    reprocess(id);
    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    assertThat(status(id)).isEqualTo("INDEXED");
  }

  /** ING-8: transient failures are retried, so a short outage does not fail the document. */
  @Test
  void transientEmbeddingFailuresAreRetried() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 5))));
    model.failNext(() -> new TransientAiException("503 unavailable"), 3);

    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    assertThat(status(id)).isEqualTo("INDEXED");
  }

  /** ING-8: after the last retry the document fails. */
  @Test
  void aFailureThatOutlastsTheRetriesFailsTheDocument() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 5))));
    model.failNext(() -> new TransientAiException("503 unavailable"), 4);

    assertThat(processNext()).isEqualTo(Outcome.FAILED);
    assertThat(document(id)).containsEntry("failure_code", "EMBEDDING_ERROR");
    assertThat(model.requests()).hasSize(4);
  }

  /**
   * 19.2: a document being reprocessed is not retrievable, and it is never INDEXED with a partial
   * set of chunks. The model is paused in the middle of the new run: the document is PROCESSING,
   * the old chunks are all there, and no transaction or connection is held (4.5). Afterwards every
   * chunk is new.
   */
  @Test
  void aDocumentBeingReprocessedIsNotRetrievableAndNeverHasAPartialSet() throws Exception {
    UUID id = upload(fiveChunkPdf());
    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    List<Long> before = chunks(id).stream().map(StoredChunk::id).toList();
    assertThat(before).hasSizeGreaterThan(2);
    assertThat(retrievableChunks(id)).isEqualTo(before.size());

    reprocess(id);
    assertThat(retrievableChunks(id)).isZero();
    CountDownLatch paused = model.pauseAtCall(1);
    CompletableFuture<Outcome> run = CompletableFuture.supplyAsync(this::processNext);
    assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();

    assertThat(status(id)).isEqualTo("PROCESSING");
    assertThat(retrievableChunks(id)).isZero();
    assertThat(chunks(id).stream().map(StoredChunk::id).toList()).isEqualTo(before);
    assertThat(((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections())
        .as("connections held while the model works")
        .isZero();

    model.resume();
    assertThat(run.get(10, TimeUnit.SECONDS)).isEqualTo(Outcome.INDEXED);
    List<StoredChunk> after = chunks(id);
    assertThat(after)
        .hasSize(before.size())
        .extracting(StoredChunk::id)
        .doesNotContainAnyElementsOf(before);
    assertThat(document(id)).containsEntry("chunk_count", after.size());
    assertThat(model.callsInTransaction()).isZero();
  }

  /**
   * ING-3: a run that fails half-way writes none of its chunks; the old set stays, unretrievable.
   */
  @Test
  void aRunThatFailsHalfWayLeavesTheOldChunksAndIsNotRetrievable() {
    UUID id = upload(fiveChunkPdf());
    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    List<Long> before = chunks(id).stream().map(StoredChunk::id).toList();

    reprocess(id);
    model.failAtCall(1, () -> new NonTransientAiException("400 bad request"));
    assertThat(processNext()).isEqualTo(Outcome.FAILED);

    assertThat(chunks(id).stream().map(StoredChunk::id).toList()).isEqualTo(before);
    assertThat(retrievableChunks(id)).isZero();
  }

  /**
   * 19.2: a stuck PROCESSING document is recovered. The run's worker "died": its heartbeat is set
   * back past the stuck timeout. Recovery queues the document again with one attempt counted; the
   * dead run can no longer write; the next run indexes it.
   */
  @Test
  void aStuckDocumentIsRecoveredAndTheDeadRunCannotWriteAnyMore() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 5))));
    ProcessingRun dead = processing.claimNext().orElseThrow();
    backdateHeartbeat(id);

    assertThat(processing.recoverStuck(java.time.Duration.ofMinutes(5), 3)).isOne();
    assertThat(document(id))
        .containsEntry("status", "UPLOADED")
        .containsEntry("processing_attempts", 1)
        .containsEntry("processing_run_id", null);

    assertThat(pipeline.process(dead, RunControl.running())).isEqualTo(Outcome.DISCARDED);
    assertThat(chunks(id)).isEmpty();
    assertThat(status(id)).isEqualTo("UPLOADED");

    assertThat(processNext()).isEqualTo(Outcome.INDEXED);
    assertThat(document(id))
        .containsEntry("status", "INDEXED")
        .containsEntry("processing_attempts", 1);
  }

  /** ING-2, ADR-030: after the last allowed attempt, recovery fails it with PROCESSING_TIMEOUT. */
  @Test
  void recoveryFailsADocumentThatRanOutOfAttempts() {
    UUID id = upload(TestPdfs.unique());
    jdbc.sql("UPDATE document SET processing_attempts = 2 WHERE id = :id").param("id", id).update();
    processing.claimNext().orElseThrow();
    backdateHeartbeat(id);

    processing.recoverStuck(java.time.Duration.ofMinutes(5), 3);

    assertThat(document(id))
        .containsEntry("status", "FAILED")
        .containsEntry("failure_code", "PROCESSING_TIMEOUT")
        .containsEntry("processing_attempts", 3);
  }

  /** ING-2: a live run's heartbeat is fresh, so recovery leaves it alone. */
  @Test
  void recoveryLeavesARunWithAFreshHeartbeat() {
    UUID id = upload(TestPdfs.unique());
    ProcessingRun run = processing.claimNext().orElseThrow();

    assertThat(processing.recoverStuck(java.time.Duration.ofMinutes(5), 3)).isZero();
    assertThat(processing.heartbeat(List.of(run.runId()))).containsExactly(run.runId());
    assertThat(status(id)).isEqualTo("PROCESSING");
  }

  /** 19.2: two workers never claim the same document (ING-1). */
  @Test
  void twoWorkersNeverClaimTheSameDocument() throws Exception {
    Set<UUID> queued = new HashSet<>();
    for (int i = 0; i < 25; i++) {
      queued.add(upload(TestPdfs.unique()));
    }
    List<CompletableFuture<List<UUID>>> workers = new ArrayList<>();
    for (int w = 0; w < 4; w++) {
      workers.add(
          CompletableFuture.supplyAsync(
              () -> {
                List<UUID> claimed = new ArrayList<>();
                for (var run = processing.claimNext();
                    run.isPresent();
                    run = processing.claimNext()) {
                  claimed.add(run.get().documentId());
                }
                return claimed;
              }));
    }
    List<UUID> all = new ArrayList<>();
    for (CompletableFuture<List<UUID>> worker : workers) {
      all.addAll(worker.get(30, TimeUnit.SECONDS));
    }

    assertThat(all).doesNotHaveDuplicates();
    assertThat(new HashSet<>(all)).isEqualTo(queued);
  }

  /**
   * An edit during processing writes only what it changed (DocumentProcessing, @DynamicUpdate), so
   * the run keeps its lease and finishes.
   */
  @Test
  void editingADocumentDuringProcessingDoesNotDisturbTheRun() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 5))));
    ProcessingRun run = processing.claimNext().orElseThrow();

    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                HttpMethod.PATCH,
                "/api/v1/documents/" + id,
                "{\"title\": \"Renamed\", \"archived\": true}"))
        .hasStatusOk();
    assertThat(document(id))
        .containsEntry("status", "PROCESSING")
        .containsEntry("processing_run_id", run.runId());

    assertThat(pipeline.process(run, RunControl.running())).isEqualTo(Outcome.INDEXED);
  }

  /** A document deleted while it is processed: the run is discarded and leaves nothing behind. */
  @Test
  void aDocumentDeletedDuringProcessingIsDiscarded() throws Exception {
    UUID id = upload(fiveChunkPdf());
    CountDownLatch paused = model.pauseAtCall(1);
    CompletableFuture<Outcome> run = CompletableFuture.supplyAsync(this::processNext);
    assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();

    assertThat(TestDocuments.send(mvc, faculty, HttpMethod.DELETE, "/api/v1/documents/" + id, null))
        .hasStatus2xxSuccessful();
    model.resume();

    assertThat(run.get(10, TimeUnit.SECONDS)).isEqualTo(Outcome.DISCARDED);
    assertThat(chunks(id)).isEmpty();
  }

  /** A stopping application gives the document back without counting an attempt (ADR-034). */
  @Test
  void aRunStoppedByShutdownGivesTheDocumentBack() {
    UUID id = upload(TestPdfs.withPageLines(List.of(lines("page1marker", 5))));
    ProcessingRun run = processing.claimNext().orElseThrow();
    RunControl control = RunControl.running();
    control.shutDown();

    assertThat(pipeline.process(run, control)).isEqualTo(Outcome.RELEASED);

    assertThat(document(id))
        .containsEntry("status", "UPLOADED")
        .containsEntry("processing_attempts", 0)
        .containsEntry("failure_code", null);
  }

  /** ING-10: more pages than allowed. */
  @Test
  void tooManyPagesFails() {
    List<List<String>> pages = new ArrayList<>();
    for (int p = 0; p < 7; p++) {
      pages.add(List.of("Page " + p + " text of the notice."));
    }
    UUID id = upload(TestPdfs.withPageLines(pages));

    assertThat(processNext()).isEqualTo(Outcome.FAILED);
    assertThat(document(id))
        .containsEntry("failure_code", "TOO_MANY_PAGES")
        .containsEntry("failure_message", "The PDF has 7 pages; at most 6 can be processed.");
  }

  /** ING-10: more chunks than allowed. Nothing is embedded. */
  @Test
  void tooManyChunksFails() {
    List<List<String>> pages = new ArrayList<>();
    for (int p = 0; p < 6; p++) {
      pages.add(lines("page" + p + "marker", 55));
    }
    UUID id = upload(TestPdfs.withPageLines(pages));

    assertThat(processNext()).isEqualTo(Outcome.FAILED);
    assertThat(document(id)).containsEntry("failure_code", "TOO_MANY_CHUNKS");
    assertThat(model.requests()).isEmpty();
  }

  /** A stored file that has disappeared fails with EXTRACTION_ERROR and a readable message. */
  @Test
  void aMissingStoredFileFailsWithExtractionError() {
    UUID id = upload(TestPdfs.unique());
    storage.delete(storageKey(id));

    assertThat(processNext()).isEqualTo(Outcome.FAILED);
    assertThat(document(id))
        .containsEntry("failure_code", "EXTRACTION_ERROR")
        .containsEntry("failure_message", "The stored file could not be read.");
  }

  /** About five chunks of 400 tokens: two pages of about 33 lines each. */
  private static byte[] fiveChunkPdf() {
    return TestPdfs.withPageLines(
        List.of(lines("page1marker", 50), lines("page2marker", 50), lines("page3marker", 30)));
  }

  private void replaceStoredFile(UUID id, byte[] content) {
    String key = storage.store(new ByteArrayInputStream(content));
    jdbc.sql("UPDATE document SET storage_key = :key WHERE id = :id")
        .param("key", key)
        .param("id", id)
        .update();
  }

  private String storageKey(UUID id) {
    return jdbc.sql("SELECT storage_key FROM document WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private void backdateHeartbeat(UUID id) {
    jdbc.sql(
            """
            UPDATE document SET processing_heartbeat_at = now() - interval '10 minutes'
            WHERE id = :id
            """)
        .param("id", id)
        .update();
  }

  private static Integer[] range(int size) {
    Integer[] indexes = new Integer[size];
    for (int i = 0; i < size; i++) {
      indexes[i] = i;
    }
    return indexes;
  }
}

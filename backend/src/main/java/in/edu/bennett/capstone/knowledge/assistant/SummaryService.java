package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import in.edu.bennett.capstone.knowledge.assistant.Summarizer.Plan;
import in.edu.bennett.capstone.knowledge.assistant.Summarizer.Progress;
import in.edu.bennett.capstone.knowledge.assistant.Summarizer.Result;
import in.edu.bennett.capstone.knowledge.assistant.Summarizer.Summary;
import in.edu.bennett.capstone.knowledge.assistant.Summarizer.SummaryFailedException;
import in.edu.bennett.capstone.knowledge.assistant.SummaryResponse.Content;
import in.edu.bennett.capstone.knowledge.assistant.SummaryResponse.Status;
import in.edu.bennett.capstone.knowledge.assistant.SummaryStore.Row;
import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.document.DocumentResponse;
import in.edu.bennett.capstone.knowledge.document.DocumentService;
import in.edu.bennett.capstone.knowledge.document.DocumentStatus;
import in.edu.bennett.capstone.knowledge.retrieval.DocumentChunk;
import in.edu.bennett.capstone.knowledge.retrieval.DocumentChunks;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Summaries of whole documents (specification 13.1, ADR-040). Deliberately not transactional: each
 * read and write is a short statement of its own, and the model calls run on the summary pool with
 * no transaction or connection held (4.5).
 *
 * <ul>
 *   <li>A request checks access first (SUM-1, ACL-E1), then that the document is indexed, its page
 *       count and the calls it would need, all before any model call. Then one statement starts a
 *       run, unless one is running or a fresh summary exists (SUM-4, SUM-5), and the run is handed
 *       to the pool.
 *   <li>A stored summary is read only after a fresh check that the user can read the document, on
 *       every request (ACL-E5), and only while it was made from the document as indexed now.
 * </ul>
 */
@Service
public class SummaryService {

  private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

  private final DocumentService documents;
  private final DocumentChunks chunks;
  private final Summarizer summarizer;
  private final SummaryStore store;
  private final ChatGenerator generator;
  private final SummaryProperties properties;
  private final ModelPrices prices;
  private final AuditService audit;
  private final TaskExecutor executor;
  private final JsonMapper json;

  SummaryService(
      DocumentService documents,
      DocumentChunks chunks,
      Summarizer summarizer,
      SummaryStore store,
      ChatGenerator generator,
      SummaryProperties properties,
      ModelPrices prices,
      AuditService audit,
      @Qualifier("summaryExecutor") TaskExecutor executor,
      JsonMapper json) {
    this.documents = documents;
    this.chunks = chunks;
    this.summarizer = summarizer;
    this.store = store;
    this.generator = generator;
    this.properties = properties;
    this.prices = prices;
    this.audit = audit;
    this.executor = executor;
    this.json = json;
  }

  /**
   * {@code POST /documents/{id}/summary}: starts a summary, or answers with the one that is being
   * made or is ready.
   *
   * @throws ApiException 404 when the user cannot read the document (ACL-8), 409 when it is not
   *     indexed, 422 when it is too large, 503 when no chat model is configured or the pool is full
   */
  public SummaryResponse request(CurrentUser user, UUID documentId) {
    DocumentResponse document = documents.get(user, documentId);
    requireIndexed(document);
    String model = requireModel();
    if (document.pageCount() != null && document.pageCount() > properties.maxPages()) {
      throw tooLarge(
          "This document has %d pages. Summaries are limited to %d pages."
              .formatted(document.pageCount(), properties.maxPages()));
    }
    List<DocumentChunk> text = chunks.of(user, documentId);
    if (text.isEmpty()) {
      requireIndexed(null);
    }
    Plan plan = summarizer.plan(text);
    if (plan.modelCalls() > properties.maxModelCalls()) {
      throw tooLarge(
          ("This document is too long to summarize: it would need %d model calls, and a summary"
                  + " may make at most %d.")
              .formatted(plan.modelCalls(), properties.maxModelCalls()));
    }
    Optional<UUID> started =
        store.start(
            documentId, model, Summarizer.PROMPT_VERSION, text.getFirst().indexedAt(), user.id());
    audit.record(
        AuditEvent.of(AuditAction.SUMMARY_REQUESTED, AuditOutcome.SUCCESS, user.id(), user.email())
            .withResource(DocumentService.RESOURCE_DOCUMENT, documentId)
            .withDetails(Map.of("started", started.isPresent(), "modelCalls", plan.modelCalls())));
    if (started.isEmpty()) {
      return read(document, model);
    }
    submit(started.get(), user, document, plan);
    // The run this request started is being made: say so, instead of reading the row again, which
    // a fast run may already have finished or failed, so the answer would depend on timing.
    return new SummaryResponse(
        documentId, Status.PENDING, null, model, Summarizer.PROMPT_VERSION, null, null, null, null);
  }

  /** {@code GET /documents/{id}/summary}: a fresh access check, then what is stored (ACL-E5). */
  public SummaryResponse get(CurrentUser user, UUID documentId) {
    DocumentResponse document = documents.get(user, documentId);
    String model = generator.configuredModel();
    if (model == null) {
      return SummaryResponse.none(documentId);
    }
    return read(document, model);
  }

  /** After a restart no run is alive: summaries still being made failed with it. */
  @EventListener(ApplicationReadyEvent.class)
  void failInterrupted() {
    int interrupted = store.failInterrupted();
    if (interrupted > 0) {
      log.info("Marked {} summaries interrupted by the restart as failed", interrupted);
    }
  }

  private SummaryResponse read(DocumentResponse document, String model) {
    if (document.status() != DocumentStatus.INDEXED || document.indexedAt() == null) {
      return SummaryResponse.none(document.id());
    }
    Optional<Row> found = store.find(document.id(), model, Summarizer.PROMPT_VERSION);
    if (found.isEmpty() || !found.get().sourceIndexedAt().equals(document.indexedAt())) {
      // SUM-4: re-indexing replaced the chunks the summary was made from.
      return SummaryResponse.none(document.id());
    }
    Row row = found.get();
    Status status = Status.valueOf(row.status());
    Content content = null;
    String path = null;
    if (status == Status.READY) {
      Stored stored = json.readValue(row.content(), Stored.class);
      Summary summary = stored.summary();
      content =
          new Content(
              summary.overview(),
              summary.keyPoints(),
              summary.rules(),
              summary.dates(),
              summary.sources().stream()
                  .map(
                      source ->
                          SourceResponse.of(
                              source.ordinal(),
                              document.id(),
                              document.title(),
                              source.pageStart(),
                              source.pageEnd(),
                              source.snippet()))
                  .toList());
      path = stored.path().name();
    }
    return new SummaryResponse(
        document.id(),
        status,
        content,
        row.model(),
        row.promptVersion(),
        path,
        status == Status.PENDING ? null : row.modelCalls(),
        row.finishedAt(),
        status == Status.FAILED ? row.failureMessage() : null);
  }

  /** What is stored as the content: the summary and how it was made. */
  record Stored(Summary summary, Summarizer.Path path, int groups) {}

  private void submit(UUID summaryId, CurrentUser user, DocumentResponse document, Plan plan) {
    try {
      executor.execute(() -> run(summaryId, user, document, plan));
    } catch (TaskRejectedException e) {
      store.failed(
          summaryId, "Too many summaries are being made right now. Try again soon.", 0, null);
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "BUSY",
          "Too many summaries are being made right now. Try again in a minute.");
    }
  }

  /** On the summary pool: the model calls, then one statement that stores the outcome. */
  private void run(UUID summaryId, CurrentUser user, DocumentResponse document, Plan plan) {
    long started = System.nanoTime();
    try {
      Result result = summarizer.summarize(document.title(), plan);
      String content =
          json.writeValueAsString(new Stored(result.summary(), plan.path(), plan.groups().size()));
      boolean stored = store.ready(summaryId, content, result.modelCalls(), result.usage());
      logRun(
          user,
          document,
          summaryId,
          stored ? "READY" : "DISCARDED",
          plan,
          new Progress(result.modelCalls(), result.model(), result.usage()),
          result.itemsKept(),
          result.itemsDropped(),
          started,
          null);
    } catch (SummaryFailedException e) {
      Progress progress = e.progress();
      store.failed(summaryId, e.getMessage(), progress.modelCalls(), progress.usage());
      logRun(user, document, summaryId, "FAILED", plan, progress, 0, 0, started, e.getMessage());
    } catch (RuntimeException e) {
      log.error("Summary {} of document {} failed", summaryId, document.id(), e);
      store.failed(summaryId, "The summary could not be made. Try again.", 0, null);
    }
  }

  /**
   * One line per summary with what it cost, so the price of summaries can be read from the log. Ids
   * and numbers only: no document text, prompt or summary (SEC-10).
   */
  private void logRun(
      CurrentUser user,
      DocumentResponse document,
      UUID summaryId,
      String outcome,
      Plan plan,
      Progress progress,
      int itemsKept,
      int itemsDropped,
      long startedNanos,
      String failure) {
    Usage usage = progress.usage() == null ? new Usage(null, null, null) : progress.usage();
    BigDecimal cost = prices.cost(usage);
    Object[] fields = {
      user.id(),
      document.id(),
      summaryId,
      outcome,
      plan.path(),
      plan.groups().size(),
      plan.tokens(),
      progress.modelCalls(),
      progress.model(),
      usage.inputTokens(),
      usage.cachedInputTokens(),
      usage.outputTokens(),
      cost.toPlainString(),
      itemsKept,
      itemsDropped,
      TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
    };
    String line =
        "Summary user={} document={} summary={} outcome={} path={} groups={} textTokens={}"
            + " modelCalls={} model={} inputTokens={} cachedInputTokens={} outputTokens={}"
            + " costUsd={} itemsKept={} itemsDropped={} ms={}";
    if (failure == null) {
      log.info(line, fields);
    } else {
      log.warn(line + " failure=\"" + failure.replace("{", "(").replace("}", ")") + "\"", fields);
    }
  }

  private String requireModel() {
    String model = generator.configuredModel();
    if (model == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          "Summaries are not available right now: no chat model is configured.");
    }
    return model;
  }

  /** SUM-1: only indexed documents; archived ones are allowed (ACL-4). */
  private static void requireIndexed(DocumentResponse document) {
    if (document == null || document.status() != DocumentStatus.INDEXED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "DOCUMENT_NOT_INDEXED",
          "This document is not ready yet. It can be summarized once it is processed.");
    }
  }

  private static ApiException tooLarge(String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_TOO_LARGE", message);
  }
}

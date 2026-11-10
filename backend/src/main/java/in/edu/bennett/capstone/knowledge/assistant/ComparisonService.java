package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import in.edu.bennett.capstone.knowledge.analytics.QueryLog;
import in.edu.bennett.capstone.knowledge.analytics.QueryOutcome;
import in.edu.bennett.capstone.knowledge.analytics.QueryRecord;
import in.edu.bennett.capstone.knowledge.analytics.QueryType;
import in.edu.bennett.capstone.knowledge.assistant.Answer.Usage;
import in.edu.bennett.capstone.knowledge.assistant.Comparer.Alignment;
import in.edu.bennett.capstone.knowledge.assistant.Comparer.Checked;
import in.edu.bennett.capstone.knowledge.assistant.Comparer.ComparisonFailedException;
import in.edu.bennett.capstone.knowledge.assistant.Comparer.Result;
import in.edu.bennett.capstone.knowledge.assistant.ComparisonResponse.Difference;
import in.edu.bennett.capstone.knowledge.assistant.ComparisonResponse.DocumentRef;
import in.edu.bennett.capstone.knowledge.assistant.ComparisonResponse.Side;
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
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Comparing two documents (specification 13.2, ADR-040). Deliberately not transactional: the access
 * checks and the chunk reads are short statements of their own, and the one model call runs with no
 * transaction or connection held (4.5). Nothing is stored: a comparison is made when asked.
 */
@Service
public class ComparisonService {

  private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);

  private final DocumentService documents;
  private final DocumentChunks chunks;
  private final Comparer comparer;
  private final ChatGenerator generator;
  private final ComparisonProperties properties;
  private final ModelPrices prices;
  private final AuditService audit;
  private final QueryLog queryLog;

  ComparisonService(
      DocumentService documents,
      DocumentChunks chunks,
      Comparer comparer,
      ChatGenerator generator,
      ComparisonProperties properties,
      ModelPrices prices,
      AuditService audit,
      QueryLog queryLog) {
    this.documents = documents;
    this.chunks = chunks;
    this.comparer = comparer;
    this.generator = generator;
    this.properties = properties;
    this.prices = prices;
    this.audit = audit;
    this.queryLog = queryLog;
  }

  /**
   * @throws ApiException 404 when the user cannot read either document, the same answer for both
   *     (ACL-8); 409 when one is not indexed; 422 when they are too large to compare (CMP-5); 503
   *     when no chat model is configured or it fails
   */
  public ComparisonResponse compare(CurrentUser user, ComparisonRequest request) {
    long requested = System.nanoTime();
    DocumentResponse left = documents.get(user, request.leftId());
    DocumentResponse right = documents.get(user, request.rightId());
    requireIndexed(left);
    requireIndexed(right);
    if (generator.configuredModel() == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          "Comparisons are not available right now: no chat model is configured.");
    }
    requireSmall(left);
    requireSmall(right);
    List<DocumentChunk> leftText = chunks.of(user, left.id());
    List<DocumentChunk> rightText = chunks.of(user, right.id());
    if (leftText.isEmpty()) {
      requireIndexed(null);
    }
    if (rightText.isEmpty()) {
      requireIndexed(null);
    }
    int tokens =
        leftText.stream().mapToInt(DocumentChunk::tokenCount).sum()
            + rightText.stream().mapToInt(DocumentChunk::tokenCount).sum();
    if (tokens > properties.maxTokens()) {
      throw tooLarge(
          "These documents are too large to compare: together they hold about %d tokens of text,"
                  .formatted(tokens)
              + " and the limit is %d.".formatted(properties.maxTokens()));
    }
    Alignment alignment =
        comparer.align(leftText, rightText, chunks.similarities(user, left.id(), right.id()));
    long started = System.nanoTime();
    Result result;
    try {
      result = comparer.compare(left.title(), right.title(), leftText, rightText, alignment);
    } catch (ComparisonFailedException e) {
      logRun(
          user, left, right, "ERROR", alignment, 1, e.model(), e.usage(), 0, 0, started, requested);
      // A failed comparison was still asked for, and may have cost a call (14.2).
      audit.recordSeparately(
          AuditEvent.of(
                  AuditAction.COMPARISON_REQUESTED, AuditOutcome.FAILURE, user.id(), user.email())
              .withResource(DocumentService.RESOURCE_DOCUMENT, left.id())
              .withDetails(Map.of("rightDocumentId", right.id().toString())));
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "MODEL_UNAVAILABLE",
          "The comparison could not be made right now. Try again later.");
    }
    logRun(
        user,
        left,
        right,
        "COMPARED",
        alignment,
        result.modelCalls(),
        result.model(),
        result.usage(),
        result.differences().size(),
        result.dropped(),
        started,
        requested);
    audit.record(
        AuditEvent.of(
                AuditAction.COMPARISON_REQUESTED, AuditOutcome.SUCCESS, user.id(), user.email())
            .withResource(DocumentService.RESOURCE_DOCUMENT, left.id())
            .withDetails(
                Map.of(
                    "rightDocumentId", right.id().toString(),
                    "differences", result.differences().size())));
    return new ComparisonResponse(
        new DocumentRef(left.id(), left.title()),
        new DocumentRef(right.id(), right.title()),
        result.differences().stream().map(d -> difference(d, left, right)).toList(),
        alignment.candidates().size(),
        alignment.identical(),
        result.dropped(),
        result.modelCalls(),
        result.model(),
        result.modelCalls() == 0 ? null : Comparer.PROMPT_VERSION);
  }

  private static Difference difference(
      Checked checked, DocumentResponse left, DocumentResponse right) {
    return new Difference(
        checked.category(),
        checked.explanation(),
        side(checked.leftQuote(), checked.left(), left, 1),
        side(checked.rightQuote(), checked.right(), right, 2));
  }

  /** One side: the quote as stored, and its pages from the stored chunk (RAG-3). */
  private static Side side(
      String quote, DocumentChunk chunk, DocumentResponse document, int ordinal) {
    if (quote == null || chunk == null) {
      return null;
    }
    return new Side(
        quote,
        SourceResponse.of(
            ordinal, document.id(), document.title(), chunk.pageStart(), chunk.pageEnd(), quote));
  }

  /**
   * One line per comparison with what it cost. Ids and numbers only: no document text, prompt or
   * explanation (SEC-10).
   */
  private void logRun(
      CurrentUser user,
      DocumentResponse left,
      DocumentResponse right,
      String outcome,
      Alignment alignment,
      int modelCalls,
      String model,
      Usage usage,
      int kept,
      int dropped,
      long startedNanos,
      long requestedNanos) {
    Usage counted = usage == null ? new Usage(null, null, null) : usage;
    BigDecimal cost = prices.cost(counted);
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    queryLog.record(
        new QueryRecord(
            user.id(),
            QueryType.COMPARE,
            "COMPARED".equals(outcome) ? QueryOutcome.OK : QueryOutcome.ERROR,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestedNanos),
            null,
            modelCalls == 0 ? null : millis,
            null,
            null,
            model,
            modelCalls,
            counted.inputTokens(),
            counted.cachedInputTokens(),
            counted.outputTokens(),
            cost));
    log.info(
        "Comparison user={} left={} right={} outcome={} candidates={} identical={} modelCalls={}"
            + " model={} inputTokens={} cachedInputTokens={} outputTokens={} costUsd={}"
            + " differences={} dropped={} ms={}",
        user.id(),
        left.id(),
        right.id(),
        outcome,
        alignment.candidates().size(),
        alignment.identical(),
        modelCalls,
        model,
        counted.inputTokens(),
        counted.cachedInputTokens(),
        counted.outputTokens(),
        cost.toPlainString(),
        kept,
        dropped,
        millis);
  }

  /** CMP-1: both indexed; archived ones are allowed (ACL-4). */
  private static void requireIndexed(DocumentResponse document) {
    if (document == null || document.status() != DocumentStatus.INDEXED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "DOCUMENT_NOT_INDEXED",
          document == null
              ? "A document is not ready yet. Documents can be compared once they are processed."
              : "\"%s\" is not ready yet. Documents can be compared once they are processed."
                  .formatted(document.title()));
    }
  }

  /** CMP-5: the page limit, before any text is read. */
  private void requireSmall(DocumentResponse document) {
    if (document.pageCount() != null && document.pageCount() > properties.maxPages()) {
      throw tooLarge(
          "\"%s\" has %d pages, too large to compare: the limit is %d pages per document."
              .formatted(document.title(), document.pageCount(), properties.maxPages()));
    }
  }

  private static ApiException tooLarge(String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_TOO_LARGE", message);
  }
}

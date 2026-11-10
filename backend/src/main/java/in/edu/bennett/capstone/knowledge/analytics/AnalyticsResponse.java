package in.edu.bennett.capstone.knowledge.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The figures of specification 14.3, every one from a real table. Days are calendar days in the
 * reader's zone, with every day of the period present, so a day with nothing shows as zero. No
 * figure is about the text of questions (D14).
 */
public record AnalyticsResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) Period period,
    @Schema(requiredMode = RequiredMode.REQUIRED) Totals totals,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<QuestionDay> questionsPerDay,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<QueryDay> queriesPerDay,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<CostDay> costPerDay,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<CountDay> uploadsPerDay,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Latency> latency,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Count> documentsByStatus,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Count> failuresByCode,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Count> usersByRole,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<CitedDocument> mostCitedDocuments,
    @Schema(requiredMode = RequiredMode.REQUIRED) SystemInfo system) {

  /**
   * @param from the first day, in {@code zone}
   * @param to the last day, today, in {@code zone}
   * @param firstRecordedAt the oldest query log row; questions and searches before it were never
   *     logged. Null when nothing is logged yet
   */
  public record Period(
      @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate from,
      @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate to,
      @Schema(requiredMode = RequiredMode.REQUIRED) String zone,
      Instant firstRecordedAt) {}

  /**
   * The period as a whole.
   *
   * @param noAnswer questions that ended "No answer found" ({@code INSUFFICIENT_EVIDENCE})
   * @param insufficientEvidenceRate {@code noAnswer} over the questions answered or not; null when
   *     there were none. Failed questions are left out: they say nothing about the documents
   * @param costUsd the cost of every model call, from the tokens each row logged
   */
  public record Totals(
      @Schema(requiredMode = RequiredMode.REQUIRED) long questions,
      @Schema(requiredMode = RequiredMode.REQUIRED) long answered,
      @Schema(requiredMode = RequiredMode.REQUIRED) long noAnswer,
      @Schema(requiredMode = RequiredMode.REQUIRED) long failedQuestions,
      Double insufficientEvidenceRate,
      @Schema(requiredMode = RequiredMode.REQUIRED) long searches,
      @Schema(requiredMode = RequiredMode.REQUIRED) long summaries,
      @Schema(requiredMode = RequiredMode.REQUIRED) long comparisons,
      @Schema(requiredMode = RequiredMode.REQUIRED) long modelCalls,
      @Schema(requiredMode = RequiredMode.REQUIRED) long inputTokens,
      @Schema(requiredMode = RequiredMode.REQUIRED) long cachedInputTokens,
      @Schema(requiredMode = RequiredMode.REQUIRED) long outputTokens,
      @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal costUsd) {}

  public record QuestionDay(
      @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate date,
      @Schema(requiredMode = RequiredMode.REQUIRED) long answered,
      @Schema(requiredMode = RequiredMode.REQUIRED) long noAnswer,
      @Schema(requiredMode = RequiredMode.REQUIRED) long failed) {}

  /** Queries per day by type (14.3). */
  public record QueryDay(
      @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate date,
      @Schema(requiredMode = RequiredMode.REQUIRED) long keywordSearches,
      @Schema(requiredMode = RequiredMode.REQUIRED) long semanticSearches,
      @Schema(requiredMode = RequiredMode.REQUIRED) long hybridSearches,
      @Schema(requiredMode = RequiredMode.REQUIRED) long questions,
      @Schema(requiredMode = RequiredMode.REQUIRED) long summaries,
      @Schema(requiredMode = RequiredMode.REQUIRED) long comparisons) {}

  public record CostDay(
      @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate date,
      @Schema(requiredMode = RequiredMode.REQUIRED) long modelCalls,
      @Schema(requiredMode = RequiredMode.REQUIRED) long inputTokens,
      @Schema(requiredMode = RequiredMode.REQUIRED) long cachedInputTokens,
      @Schema(requiredMode = RequiredMode.REQUIRED) long outputTokens,
      @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal costUsd) {}

  public record CountDay(
      @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate date,
      @Schema(requiredMode = RequiredMode.REQUIRED) long count) {}

  /**
   * Latency in milliseconds by query type and stage (14.3, 21): the median and the 95th percentile
   * of the period's rows. A stage that did not run in any row is null.
   */
  public record Latency(
      @Schema(requiredMode = RequiredMode.REQUIRED) QueryType type,
      @Schema(requiredMode = RequiredMode.REQUIRED) long count,
      Double totalP50,
      Double totalP95,
      Double retrievalP50,
      Double retrievalP95,
      Double generationP50,
      Double generationP95) {}

  public record Count(
      @Schema(requiredMode = RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = RequiredMode.REQUIRED) long count) {}

  /** A document cited in the period's answers, with its title now. */
  public record CitedDocument(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID documentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String title,
      @Schema(requiredMode = RequiredMode.REQUIRED) long citations) {}

  /**
   * 14.1 "System": what is configured and how much is indexed.
   *
   * @param chatModel null when no chat model is configured
   * @param chunks the passages stored for search
   */
  public record SystemInfo(
      String chatModel,
      @Schema(requiredMode = RequiredMode.REQUIRED) String embeddingModel,
      @Schema(requiredMode = RequiredMode.REQUIRED) int embeddingDimensions,
      @Schema(requiredMode = RequiredMode.REQUIRED) long documents,
      @Schema(requiredMode = RequiredMode.REQUIRED) long chunks) {}
}

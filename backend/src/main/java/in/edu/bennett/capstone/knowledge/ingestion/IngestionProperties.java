package in.edu.bennett.capstone.knowledge.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ingestion settings (specification 18, {@code app.ingestion}; section 9, ADR-034).
 *
 * @param enabled whether this instance runs the worker. Tests turn it off and drive the pipeline
 *     themselves
 * @param chunkSizeTokens the largest chunk, counted with the cl100k tokenizer (D10, ING-6)
 * @param chunkOverlapTokens how much of a chunk's end the next chunk repeats
 * @param embeddingBatchSize chunks embedded per request (ING-8)
 * @param parallelDocuments documents processed at the same time by this instance (ING-12)
 * @param pollInterval how often the worker looks for queued documents, besides being woken after an
 *     upload
 * @param heartbeatInterval how often a running run proves it is alive
 * @param recoveryInterval how often stuck runs are looked for
 * @param stuckTimeout a run whose heartbeat is older than this belongs to a dead worker (ING-2)
 * @param maxAttempts interrupted runs allowed before {@code PROCESSING_TIMEOUT} (ING-2)
 * @param maxPages more pages fail with {@code TOO_MANY_PAGES} (ING-10)
 * @param maxChunks more chunks fail with {@code TOO_MANY_CHUNKS} (ING-10)
 * @param minCharsPerPage fewer characters per page on average fail with {@code NO_TEXT_LAYER}
 *     (ING-4)
 * @param shutdownWait how long a stopping application waits for running documents
 */
@Validated
@ConfigurationProperties("app.ingestion")
public record IngestionProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("400") @Positive int chunkSizeTokens,
    @DefaultValue("60") @PositiveOrZero int chunkOverlapTokens,
    @DefaultValue("16") @Positive int embeddingBatchSize,
    @DefaultValue("2") @Positive int parallelDocuments,
    @DefaultValue("10s") @NotNull Duration pollInterval,
    @DefaultValue("30s") @NotNull Duration heartbeatInterval,
    @DefaultValue("1m") @NotNull Duration recoveryInterval,
    @DefaultValue("5m") @NotNull Duration stuckTimeout,
    @DefaultValue("3") @Positive int maxAttempts,
    @DefaultValue("500") @Positive int maxPages,
    @DefaultValue("5000") @Positive int maxChunks,
    @DefaultValue("20") @PositiveOrZero int minCharsPerPage,
    @DefaultValue("20s") @NotNull Duration shutdownWait) {

  @Schema(hidden = true)
  @AssertTrue(message = "chunk-overlap-tokens must be smaller than chunk-size-tokens")
  public boolean isOverlapSmallerThanChunk() {
    return chunkOverlapTokens < chunkSizeTokens;
  }

  /** A live run must have several chances to refresh its heartbeat before it counts as stuck. */
  @Schema(hidden = true)
  @AssertTrue(message = "stuck-timeout must be at least four heartbeat intervals")
  public boolean isStuckTimeoutLongEnough() {
    return stuckTimeout == null
        || heartbeatInterval == null
        || stuckTimeout.compareTo(heartbeatInterval.multipliedBy(4)) >= 0;
  }
}

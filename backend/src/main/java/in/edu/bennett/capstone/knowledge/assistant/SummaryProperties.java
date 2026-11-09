package in.edu.bennett.capstone.knowledge.assistant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Summaries (specification 13.1, {@code app.summary}; ADR-040). The limits keep the cost of one
 * summary known before it starts: at most {@code maxModelCalls} calls, each on at most {@code
 * mapGroupTokens} tokens of text.
 *
 * @param maxPages a longer document is refused before any model call
 * @param singlePassTokens a document with at most this many tokens of text is summarized in one
 *     call (SUM-3); counted with the chunker's tokenizer (cl100k)
 * @param mapGroupTokens a longer one is split into groups of consecutive chunks of at most this
 *     many tokens, each summarized on its own (map), then the partial summaries are combined in one
 *     more call (reduce)
 * @param maxModelCalls the most calls one summary may make, the reduce included; a document that
 *     would need more is refused before any call
 * @param parallel how many summaries are made at once; more wait in a queue
 * @param queueCapacity how many may wait; beyond that a request answers 503
 */
@Validated
@ConfigurationProperties("app.summary")
public record SummaryProperties(
    @DefaultValue("40") @Min(1) @Max(2000) int maxPages,
    @DefaultValue("4000") @Min(200) @Max(100000) int singlePassTokens,
    @DefaultValue("3000") @Min(200) @Max(100000) int mapGroupTokens,
    @DefaultValue("8") @Min(1) @Max(50) int maxModelCalls,
    @DefaultValue("2") @Min(1) @Max(16) int parallel,
    @DefaultValue("20") @Min(0) @Max(1000) int queueCapacity) {

  /** A group must fit the single pass, or a document just above it could still be one group. */
  @AssertTrue(message = "map-group-tokens must not be larger than single-pass-tokens")
  public boolean isGroupWithinSinglePass() {
    return mapGroupTokens <= singlePassTokens;
  }
}

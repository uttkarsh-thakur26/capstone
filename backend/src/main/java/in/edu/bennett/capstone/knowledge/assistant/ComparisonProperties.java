package in.edu.bennett.capstone.knowledge.assistant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Comparisons (specification 13.2, {@code app.comparison}; ADR-040). A comparison makes at most one
 * model call; these limits keep that call's size known before it is made (CMP-5).
 *
 * @param maxPages a document with more pages is too large to compare
 * @param maxTokens the most tokens of text both documents may hold together, counted with the
 *     chunker's tokenizer
 * @param alignFloor two chunks that are each other's closest are paired only above this similarity,
 *     which only keeps out passages the embeddings call unrelated; the pairing itself is mutual
 *     closeness (CMP-2, ADR-040)
 * @param maxDifferences the most differences one comparison reports
 * @param maxQuoteLength the longest quote of a difference, in characters; it stays shorter than the
 *     chunks' overlap of 60 tokens, so a quote that is in a document is always inside one chunk
 */
@Validated
@ConfigurationProperties("app.comparison")
public record ComparisonProperties(
    @DefaultValue("30") @Min(1) @Max(1000) int maxPages,
    @DefaultValue("16000") @Min(500) @Max(200000) int maxTokens,
    @DefaultValue("0.2") @DecimalMin("-1.0") @DecimalMax("1.0") double alignFloor,
    @DefaultValue("15") @Min(1) @Max(100) int maxDifferences,
    @DefaultValue("240") @Min(20) @Max(2000) int maxQuoteLength) {}

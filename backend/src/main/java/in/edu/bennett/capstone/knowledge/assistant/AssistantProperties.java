package in.edu.bennett.capstone.knowledge.assistant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Question answering settings (specification 18, {@code app.assistant}). The model's own settings,
 * such as temperature, output limit, timeout and retries, differ by provider, so they are the
 * provider's properties under {@code spring.ai.*} (ADR-037).
 *
 * @param contextTokenBudget the most tokens the numbered sources may take in one prompt, counted
 *     with the chunker's tokenizer (RAG-5). Six chunks of 400 tokens fit with their tags
 * @param abstentionMessage the fixed answer for insufficient evidence (RAG-2). It never mentions
 *     documents the user cannot read
 * @param snippetLength the most characters of a cited chunk stored as the citation's snippet
 */
@Validated
@ConfigurationProperties("app.assistant")
public record AssistantProperties(
    @DefaultValue("4000") @Min(1000) @Max(100000) int contextTokenBudget,
    @DefaultValue(
            "Insufficient information was found in the available authorized documents to answer"
                + " this question.")
        @NotBlank
        String abstentionMessage,
    @DefaultValue("300") @Min(50) @Max(2000) int snippetLength) {}

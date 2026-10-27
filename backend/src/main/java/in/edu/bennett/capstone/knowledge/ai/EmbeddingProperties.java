package in.edu.bennett.capstone.knowledge.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Embedding model settings (specification 18, {@code app.embedding}; D6, ADR-033). The model is
 * named here only; the Ollama bean reads it from here (S11).
 *
 * @param model the model's name in the provider
 * @param dimensions the vector length; also the dimension of {@code document_chunk.embedding}
 *     through a Flyway placeholder (DATA-4)
 * @param maxInputTokens the most tokens the model takes in one input. Sent to Ollama as {@code
 *     num_ctx}, with truncation off, so a longer input fails instead of being cut (ING-7)
 * @param tokenizerMargin how many model tokens one token of our own tokenizer may become. The
 *     largest ratio measured on real documents was 1.36 (ADR-033)
 * @param queryPrefix put in front of a search query, never in front of document text. Qwen3
 *     embeddings expect a task instruction on queries only
 * @param retry retries of transient failures (ING-8). Spring AI's Ollama embedding model has none
 *     of its own, so this is the only layer
 */
@Validated
@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(
    @NotBlank String model,
    @Positive int dimensions,
    @DefaultValue("2048") @Positive int maxInputTokens,
    @DefaultValue("1.5") @DecimalMin("1.0") double tokenizerMargin,
    @DefaultValue(
            "Instruct: Given a question, retrieve passages from institutional documents that"
                + " answer the question\nQuery:")
        @NotNull
        String queryPrefix,
    @DefaultValue @NotNull @Valid Retry retry) {

  /**
   * Capped exponential backoff.
   *
   * @param maxRetries retries after the first call, so {@code maxRetries + 1} calls at most
   */
  public record Retry(
      @DefaultValue("3") @PositiveOrZero int maxRetries,
      @DefaultValue("1s") @NotNull Duration delay,
      @DefaultValue("2.0") @DecimalMin("1.0") double multiplier,
      @DefaultValue("10s") @NotNull Duration maxDelay) {}
}

package in.edu.bennett.capstone.knowledge.ai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Turns text into vectors (ING-8, D6). Documents are embedded as they are; queries get the model's
 * instruction prefix first (ADR-033). Every request carries the provider options of {@link
 * AiConfig}, so input is never truncated.
 *
 * <p>Transient failures (timeouts, refused connections, server errors, and 408 or 429 through
 * {@code spring.ai.retry.on-http-codes}) are retried with capped exponential backoff. The retry
 * template is private: a bean would replace Spring AI's own one for the chat model of Phase 5.
 */
@Component
public class Embedder {

  private static final Logger log = LoggerFactory.getLogger(Embedder.class);

  /** A short fixed text for the startup check of the dimension (ING-9). */
  private static final String PROBE = "dimension check";

  private final EmbeddingModel model;
  private final EmbeddingOptions options;
  private final EmbeddingProperties properties;
  private final RetryTemplate retry;

  Embedder(EmbeddingModel model, EmbeddingOptions options, EmbeddingProperties properties) {
    this.model = model;
    this.options = options;
    this.properties = properties;
    EmbeddingProperties.Retry settings = properties.retry();
    this.retry =
        new RetryTemplate(
            RetryPolicy.builder()
                .includes(TransientAiException.class, ResourceAccessException.class)
                .maxRetries(settings.maxRetries())
                .delay(settings.delay())
                .multiplier(settings.multiplier())
                .maxDelay(settings.maxDelay())
                .build());
  }

  /** The configured model name, recorded on each indexed document (ING-8). */
  public String modelName() {
    return properties.model();
  }

  public int dimensions() {
    return properties.dimensions();
  }

  /**
   * Embeds document chunks in one request, in order, with no prefix.
   *
   * @throws EmbeddingFailedException when the model fails or returns something unusable
   */
  public List<float[]> embedDocuments(List<String> texts) {
    for (String text : texts) {
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("An empty text is never embedded (ING-8)");
      }
    }
    return embed(texts);
  }

  /** Embeds a search query, with the instruction prefix (first used by retrieval, Phase 4). */
  public float[] embedQuery(String query) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("An empty query is never embedded (ING-8)");
    }
    return embed(List.of(queryText(query))).getFirst();
  }

  /** What is sent for a query. */
  String queryText(String query) {
    return properties.queryPrefix() + query;
  }

  /**
   * One call without retries, for the startup check (ING-9).
   *
   * @return the length of the vector the model returns
   */
  int probeDimensions() {
    return call(List.of(PROBE)).getFirst().getOutput().length;
  }

  private List<float[]> embed(List<String> texts) {
    List<Embedding> results;
    try {
      results = retry.invoke(() -> call(texts));
    } catch (RuntimeException e) {
      log.warn("Embedding {} texts failed: {}", texts.size(), e.toString());
      throw new EmbeddingFailedException("The embedding model failed", e);
    }
    if (results.size() != texts.size()) {
      throw new EmbeddingFailedException(
          "Asked for " + texts.size() + " vectors and got " + results.size());
    }
    return results.stream().map(result -> checked(result.getOutput())).toList();
  }

  private List<Embedding> call(List<String> texts) {
    return model.call(new EmbeddingRequest(texts, options)).getResults();
  }

  private float[] checked(float[] vector) {
    if (vector == null || vector.length != properties.dimensions()) {
      throw new EmbeddingFailedException(
          "Expected a vector of "
              + properties.dimensions()
              + " dimensions and got "
              + (vector == null ? "none" : vector.length));
    }
    for (float value : vector) {
      if (!Float.isFinite(value)) {
        throw new EmbeddingFailedException("The model returned a vector that is not finite");
      }
    }
    return vector;
  }
}

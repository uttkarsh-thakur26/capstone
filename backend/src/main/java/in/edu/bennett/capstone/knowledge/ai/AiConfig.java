package in.edu.bennett.capstone.knowledge.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Model access (specification 3.4): the only place that knows the provider. Phase 5 adds the chat
 * model here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingProperties.class)
class AiConfig {

  /**
   * The Ollama embedding model, built here instead of by Spring AI because its properties cannot
   * set {@code num_ctx} (ADR-034). Declared as {@link OllamaEmbeddingModel}, so Spring AI's own
   * bean backs off. Tests select another model with {@code spring.ai.model.embedding}.
   */
  @Bean
  @ConditionalOnProperty(
      name = "spring.ai.model.embedding",
      havingValue = "ollama",
      matchIfMissing = true)
  OllamaEmbeddingModel ollamaEmbeddingModel(
      OllamaApi api,
      EmbeddingOptions embeddingRequestOptions,
      ObjectProvider<ObservationRegistry> observations) {
    return OllamaEmbeddingModel.builder()
        .ollamaApi(api)
        .options((OllamaEmbeddingOptions) embeddingRequestOptions)
        .observationRegistry(observations.getIfUnique(() -> ObservationRegistry.NOOP))
        .build();
  }

  /**
   * The options sent with every embedding request. Spring AI keeps a model's default options only
   * when the request carries options of the provider's own type; its {@code embed(...)} shortcuts
   * send generic ones, which drop {@code truncate} and {@code num_ctx} without an error. {@link
   * Embedder} therefore always sends these (ADR-034).
   */
  @Bean
  @ConditionalOnProperty(
      name = "spring.ai.model.embedding",
      havingValue = "ollama",
      matchIfMissing = true)
  EmbeddingOptions embeddingRequestOptions(EmbeddingProperties properties) {
    return OllamaEmbeddingOptions.builder()
        .model(properties.model())
        .truncate(false)
        .numCtx(properties.maxInputTokens())
        .build();
  }
}

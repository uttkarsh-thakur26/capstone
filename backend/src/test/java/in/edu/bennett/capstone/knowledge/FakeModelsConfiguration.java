package in.edu.bennett.capstone.knowledge;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The fake models of specification 19.1 in place of the real ones. The test profile sets {@code
 * spring.ai.model.embedding=fake} and {@code spring.ai.model.chat=fake}, so neither Spring AI nor
 * {@code AiConfig} creates an Ollama or OpenAI model.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeModelsConfiguration {

  @Bean
  FakeChatModel fakeChatModel() {
    return new FakeChatModel();
  }

  @Bean
  FakeEmbeddingModel fakeEmbeddingModel(@Value("${app.embedding.dimensions}") int dimensions) {
    return new FakeEmbeddingModel(dimensions);
  }

  @Bean
  EmbeddingOptions embeddingRequestOptions(@Value("${app.embedding.model}") String model) {
    return EmbeddingOptions.builder().model(model).build();
  }
}

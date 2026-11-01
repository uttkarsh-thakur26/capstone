package in.edu.bennett.capstone.knowledge.assistant;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfig {

  /** Counts tokens with the chunker's tokenizer (cl100k), so the budget and chunks agree. */
  @Bean
  PromptAssembler promptAssembler(AssistantProperties properties) {
    return new PromptAssembler(new JTokkitTokenCountEstimator(), properties.contextTokenBudget());
  }
}

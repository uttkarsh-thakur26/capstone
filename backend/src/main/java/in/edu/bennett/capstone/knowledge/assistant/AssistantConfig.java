package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfig {

  /** Counts tokens with the chunker's tokenizer (cl100k), so the budget and chunks agree. */
  @Bean
  PromptAssembler promptAssembler(AssistantProperties properties) {
    return new PromptAssembler(
        new JTokkitTokenCountEstimator(),
        properties.contextTokenBudget(),
        properties.historyTokenBudget());
  }

  @Bean
  FollowUpRewriter followUpRewriter(
      ChatGenerator generator, PromptAssembler assembler, JsonMapper json) {
    return new FollowUpRewriter(generator, assembler, json);
  }
}

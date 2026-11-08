package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.ai.ChatGenerator;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  AssistantProperties.class,
  SummaryProperties.class,
  ModelPrices.class,
  ComparisonProperties.class
})
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

  @Bean
  Summarizer summarizer(
      ChatGenerator generator,
      JsonMapper json,
      SummaryProperties summaries,
      AssistantProperties assistant) {
    return new Summarizer(generator, json, summaries, assistant.snippetLength());
  }

  @Bean
  Comparer comparer(ChatGenerator generator, JsonMapper json, ComparisonProperties properties) {
    return new Comparer(generator, json, properties);
  }

  /**
   * SUM-5: summaries are made in the background on a pool of their own with {@code parallel}
   * threads, like ingestion (S13): Boot's shared executor has no limit with virtual threads. A full
   * queue refuses the request instead of letting work pile up. At shutdown a waiting summary is
   * dropped; its row is marked failed when the application starts again.
   */
  @Bean
  ThreadPoolTaskExecutor summaryExecutor(SummaryProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.parallel());
    executor.setMaxPoolSize(properties.parallel());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("summary-");
    return executor;
  }
}

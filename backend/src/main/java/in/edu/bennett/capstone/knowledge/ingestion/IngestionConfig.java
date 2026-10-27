package in.edu.bennett.capstone.knowledge.ingestion;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestionProperties.class)
class IngestionConfig {

  /** One tokenizer throughout (ING-6): cl100k, through Spring AI's jtokkit estimator. */
  @Bean
  TokenCountEstimator tokenCountEstimator() {
    return new JTokkitTokenCountEstimator();
  }

  @Bean
  Chunker chunker(TokenCountEstimator tokens, IngestionProperties properties) {
    return new TokenChunker(tokens, properties.chunkSizeTokens(), properties.chunkOverlapTokens());
  }

  /**
   * ING-12, S13: a pool of its own with exactly {@code parallel-documents} threads. Boot's
   * application executor is shared and, with virtual threads, has no limit at all. The worker hands
   * over only as many runs as threads are free, so nothing waits in a queue while it holds a claim.
   */
  @Bean
  ThreadPoolTaskExecutor ingestionExecutor(IngestionProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.parallelDocuments());
    executor.setMaxPoolSize(properties.parallelDocuments());
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("ingestion-");
    // Never interrupt a run at shutdown: the worker asks it to give its document back instead.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationMillis(properties.shutdownWait().toMillis());
    return executor;
  }
}

package in.edu.bennett.capstone.knowledge.storage;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
@EnableScheduling
class StorageConfig implements SchedulingConfigurer {

  private final StorageProperties properties;
  private final FileDeletionQueue deletions;

  StorageConfig(StorageProperties properties, FileDeletionQueue deletions) {
    this.properties = properties;
    this.deletions = deletions;
  }

  @Bean
  static FileStorage fileStorage(StorageProperties properties) {
    return new LocalFileStorage(Path.of(properties.root()));
  }

  /**
   * DOC-9: failed deletions are retried every interval, the first time one interval after start.
   */
  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    Duration interval = properties.cleanupInterval();
    registrar.addFixedDelayTask(new FixedDelayTask(deletions::retryPending, interval, interval));
  }
}

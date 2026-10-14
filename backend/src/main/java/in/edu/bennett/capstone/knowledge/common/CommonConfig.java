package in.edu.bennett.capstone.knowledge.common;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
class CommonConfig {

  /** All time comes from this clock, in UTC (specification 4.5). Tests can replace it. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}

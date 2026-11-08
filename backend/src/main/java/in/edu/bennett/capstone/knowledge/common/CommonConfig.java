package in.edu.bennett.capstone.knowledge.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.EnumFeature;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
class CommonConfig {

  /**
   * All time comes from this clock, in UTC (specification 4.5). Tests can replace it. It ticks in
   * whole microseconds, the precision of PostgreSQL's {@code timestamptz}, so a time returned when
   * a row is written is exactly the time read back later (ADR-040).
   */
  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }

  /**
   * Request bodies are read strictly (ADR-040): a number is never taken for an enum constant by its
   * position, so {@code "category": 7} is refused instead of becoming the eighth category, and a
   * date must be written as text, so {@code 20260701} is refused instead of being read as a count
   * of days since 1970.
   */
  @Bean
  JsonMapperBuilderCustomizer strictRequestValues() {
    return builder ->
        builder
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .withConfigOverride(
                LocalDate.class,
                override -> override.setFormat(JsonFormat.Value.forLeniency(false)));
  }
}

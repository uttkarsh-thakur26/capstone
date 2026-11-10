package in.edu.bennett.capstone.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The limits a deployment gets when no variable is set (SEC-8, ADR-041): {@code application.yaml}
 * alone, with its placeholders' defaults and no environment. The tests themselves run with higher
 * limits, so this is where the production values are checked.
 */
class RateLimitDefaultsTest {

  @Test
  void theDefaultsAreTheAgreedLimits() throws IOException {
    List<PropertySource<?>> yaml =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yaml"));
    MutablePropertySources sources = new MutablePropertySources();
    yaml.forEach(sources::addLast);
    Binder binder =
        new Binder(
            ConfigurationPropertySources.from(sources),
            new PropertySourcesPlaceholdersResolver(sources));

    RateLimitProperties limits =
        binder.bindOrCreate("app.security.rate-limits", RateLimitProperties.class);

    assertThat(limits)
        .isEqualTo(
            new RateLimitProperties(
                10,
                Duration.ofMinutes(1),
                200,
                5,
                Duration.ofMinutes(15),
                20,
                Duration.ofMinutes(15),
                50,
                Duration.ofHours(1)));
  }
}

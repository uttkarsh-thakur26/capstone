package in.edu.bennett.capstone.knowledge.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The application fails fast on missing or invalid configuration (specification 18). */
class AppPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(CommonConfig.class);

  @Test
  void bindsTheDisplayName() {
    runner
        .withPropertyValues("app.display-name=Some platform")
        .run(
            context ->
                assertThat(context.getBean(AppProperties.class).displayName())
                    .isEqualTo("Some platform"));
  }

  @Test
  void refusesToStartWithoutADisplayName() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("displayName"));
  }

  @Test
  void refusesToStartWithABlankDisplayName() {
    runner
        .withPropertyValues("app.display-name=   ")
        .run(context -> assertThat(context).hasFailed());
  }
}

package in.edu.bennett.capstone.knowledge.common;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.security.SecurityConfig;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(MetaController.class)
@ActiveProfiles("test")
@Import({CommonConfig.class, SecurityConfig.class, MetaControllerTest.FixedBuild.class})
class MetaControllerTest {

  @Autowired MockMvcTester mvc;

  @Test
  void metaIsPublicAndReturnsNameAndVersion() {
    assertThat(mvc.get().uri("/api/v1/meta"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"name": "Test platform", "version": "1.2.3-test"}
            """);
  }

  @Test
  void onlyGetIsPublicOnMeta() {
    assertThat(mvc.post().uri("/api/v1/meta")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void unknownPathsAnswer401NotDetails() {
    assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedBuild {
    @Bean
    BuildProperties buildProperties() {
      Properties properties = new Properties();
      properties.setProperty("version", "1.2.3-test");
      return new BuildProperties(properties);
    }
  }
}

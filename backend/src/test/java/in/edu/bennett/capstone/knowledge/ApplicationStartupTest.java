package in.edu.bennett.capstone.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The whole application starts against the production database image: Flyway migrates, Hibernate
 * validates the schema, and the public endpoints answer while everything else is denied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApplicationStartupTest {

  @Autowired MockMvcTester mvc;

  @Autowired BuildProperties build;

  @Test
  void healthIsPublicAndUp() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void metaReportsTheConfiguredNameAndTheBuildVersion() {
    assertThat(mvc.get().uri("/api/v1/meta"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"name": "Test platform", "version": "%s"}
            """
                .formatted(build.getVersion()));
  }

  @Test
  void endpointsOffThePublicListNeedAuthentication() {
    assertThat(mvc.get().uri("/api/v1/documents")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void apiDocumentationIsOffOutsideTheDevProfile() {
    assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatus(HttpStatus.UNAUTHORIZED);
  }
}

package in.edu.bennett.capstone.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * With API documentation switched on, as in the dev profile, the OpenAPI document is public and
 * describes {@code /api/v1/meta} and the error format. The document is also saved to {@code
 * target/openapi.json}, which the frontend turns into TypeScript types ({@code npm run gen:api},
 * specification 16.1).
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiDocsTest {

  @Autowired MockMvcTester mvc;

  @Test
  void openApiDocumentDescribesMetaAndIsSavedForTheFrontend() throws IOException {
    MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.paths['/api/v1/meta'].get").isNotNull();
    assertThat(result)
        .bodyJson()
        .extractingPath("$.components.schemas.MetaResponse.properties")
        .asMap()
        .containsOnlyKeys("name", "version");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.components.schemas.MetaResponse.required")
        .asArray()
        .containsExactlyInAnyOrder("name", "version");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.paths['/api/v1/meta'].get.responses['200'].content")
        .asMap()
        .containsOnlyKeys("application/json");
    // Errors are documented once, as ProblemResponse, on every operation (ADR-023).
    assertThat(result)
        .bodyJson()
        .extractingPath("$.paths['/api/v1/meta'].get.responses['default'].content")
        .asMap()
        .containsOnlyKeys("application/problem+json");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.components.schemas.ProblemResponse.required")
        .asArray()
        .containsExactlyInAnyOrder("type", "title", "status", "detail", "code");
    // The servlet container's error path is not part of the API.
    assertThat(result).bodyJson().extractingPath("$.paths").asMap().doesNotContainKey("/error");

    Files.writeString(
        Path.of("target", "openapi.json"),
        result.getResponse().getContentAsString(),
        StandardCharsets.UTF_8);
  }
}

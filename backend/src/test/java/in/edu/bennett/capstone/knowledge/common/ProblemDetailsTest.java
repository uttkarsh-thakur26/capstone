package in.edu.bennett.capstone.knowledge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import in.edu.bennett.capstone.knowledge.TestcontainersConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1 exit criterion: every error is a Problem Details response with a stable {@code code} and
 * no internal data (specification 15.1, ADR-023). Each path that can produce an error is covered:
 * Spring MVC, the security filter chain, and the servlet container's error path, which only a real
 * server reaches.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ProblemDetailsTest.Probe.class})
class ProblemDetailsTest {

  /** Text that would show internal data if it reached a response. */
  private static final List<String> INTERNAL_MARKERS =
      List.of(
          "Exception",
          "exception",
          "java.",
          "in.edu.bennett",
          "org.springframework",
          "SELECT",
          "app_user",
          ".java",
          "static resource");

  private static final Set<String> DOCUMENTED_FIELDS =
      Set.of(
          "type", "title", "status", "detail", "instance", "code", "errors", "retryAfterSeconds");

  @Autowired MockMvcTester mvc;

  @LocalServerPort int port;

  @Test
  void anonymousRequestGets401FromTheFilterChain() {
    MvcTestResult result = mvc.get().uri("/api/v1/anything").exchange();

    assertProblem(result, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    assertThat(result).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
  }

  @Test
  void wrongRoleGets403FromTheFilterChain() {
    MvcTestResult result =
        mvc.get().uri("/api/v1/admin/users").with(user("s1").roles("STUDENT")).exchange();

    assertProblem(result, HttpStatus.FORBIDDEN, "FORBIDDEN");
  }

  @Test
  void accessDeniedInsideAControllerGets403() {
    MvcTestResult result =
        mvc.post().uri("/api/v1/test-probe/denied").with(user("probe")).exchange();

    assertProblem(result, HttpStatus.FORBIDDEN, "FORBIDDEN");
  }

  @Test
  void unknownPathGets404() {
    MvcTestResult result = mvc.get().uri("/api/v1/does-not-exist").with(user("probe")).exchange();

    assertProblem(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
  }

  @Test
  void wrongMethodGets405() {
    MvcTestResult result = mvc.post().uri("/api/v1/meta").with(user("probe")).exchange();

    assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
  }

  @Test
  void invalidBodyGets400WithTheFieldErrors() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/test-probe/validated")
            .with(user("probe"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"\", \"nickname\": \"far too long\"}")
            .exchange();

    assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactlyInAnyOrder("name", "nickname");
    assertThat(result).bodyJson().extractingPath("$.errors[0].message").isNotNull();
  }

  @Test
  void unreadableBodyGets400() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/test-probe/validated")
            .with(user("probe"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not json")
            .exchange();

    assertProblem(result, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
  }

  @Test
  void unsupportedContentTypeGets415() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/test-probe/validated")
            .with(user("probe"))
            .contentType(MediaType.TEXT_PLAIN)
            .content("name=x")
            .exchange();

    assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void domainExceptionKeepsItsStatusCodeAndDetail() {
    MvcTestResult result =
        mvc.post().uri("/api/v1/test-probe/conflict").with(user("probe")).exchange();

    assertProblem(result, HttpStatus.CONFLICT, "PROBE_TAKEN");
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("The probe is taken.");
  }

  @Test
  void unexpectedExceptionGets500WithoutItsMessage() {
    MvcTestResult result =
        mvc.post().uri("/api/v1/test-probe/crash").with(user("probe")).exchange();

    assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL");
    assertThat(body(result)).doesNotContain("C:\\data");
  }

  @Test
  void requestRejectedBeforeAnyControllerGetsProblemDetailsFromTheErrorPath() throws Exception {
    // Spring Security's firewall rejects a semicolon in the path before any controller runs, so
    // the response comes from the servlet container's error path, which MockMvc never reaches.
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/meta;x=y"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(response.body())
        .contains("\"code\":\"BAD_REQUEST\"")
        .contains("\"status\":400")
        .doesNotContain(INTERNAL_MARKERS.toArray(String[]::new));
  }

  private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
    assertThat(result).hasStatus(status);
    assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    assertThat(result).bodyJson().extractingPath("$.title").isNotNull();
    assertThat(result).bodyJson().extractingPath("$.detail").isNotNull();
    assertThat(body(result)).doesNotContain(INTERNAL_MARKERS.toArray(String[]::new));
    // Only the fields that ProblemResponse documents, so the generated SPA type is accurate.
    assertThat(result)
        .bodyJson()
        .extractingPath("$")
        .asMap()
        .satisfies(fields -> assertThat(fields.keySet()).isSubsetOf(DOCUMENTED_FIELDS));
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new UncheckedIOException(e);
    }
  }

  record ProbeRequest(@NotBlank String name, @Size(max = 5) String nickname) {}

  /** Endpoints that fail on purpose. Nested in the test, so no other test context sees them. */
  @RestController
  static class Probe {

    @PostMapping("/api/v1/test-probe/validated")
    void validated(@Valid @RequestBody ProbeRequest request) {}

    @PostMapping("/api/v1/test-probe/denied")
    void denied() {
      throw new AccessDeniedException("probe denied");
    }

    @PostMapping("/api/v1/test-probe/conflict")
    void conflict() {
      throw new ApiException(HttpStatus.CONFLICT, "PROBE_TAKEN", "The probe is taken.");
    }

    @PostMapping("/api/v1/test-probe/crash")
    void crash() {
      throw new IllegalStateException("SELECT secret FROM app_user failed at C:\\data\\x.java");
    }
  }
}

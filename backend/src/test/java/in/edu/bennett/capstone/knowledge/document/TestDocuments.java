package in.edu.bennett.capstone.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Uploads and reads documents through the real endpoints, for integration tests. */
public final class TestDocuments {

  private TestDocuments() {}

  /** {@code POST /documents} with the given file and the {@code metadata} JSON part. */
  public static MvcTestResult upload(
      MockMvcTester mvc, Session session, byte[] content, String filename, String metadataJson) {
    return mvc.post()
        .multipart()
        .uri("/api/v1/documents")
        .header(HttpHeaders.AUTHORIZATION, session.bearer())
        .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_PDF_VALUE, content))
        .file(
            new MockMultipartFile(
                "metadata",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                metadataJson.getBytes(StandardCharsets.UTF_8)))
        .exchange();
  }

  /**
   * Uploads a new unique PDF with this title and grants and returns the document's id.
   *
   * @param grantsJson a JSON array such as {@code [{"type": "ROLE", "role": "STUDENT"}]}
   */
  public static UUID uploaded(MockMvcTester mvc, Session session, String title, String grantsJson) {
    MvcTestResult result =
        upload(mvc, session, TestPdfs.unique(), "test.pdf", metadata(title, grantsJson));
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(TestAccounts.body(result), "$.id"));
  }

  public static String metadata(String title, String grantsJson) {
    return """
        {"title": "%s", "grants": %s}
        """
        .formatted(title, grantsJson);
  }

  /** A request with the bearer token and, unless null, a JSON body. */
  public static MvcTestResult send(
      MockMvcTester mvc, Session session, HttpMethod method, String uri, String json) {
    MockMvcTester.MockMvcRequestBuilder request =
        mvc.method(method).uri(URI.create(uri)).header(HttpHeaders.AUTHORIZATION, session.bearer());
    if (json != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    return request.exchange();
  }

  /** A GET with the bearer token. The URI is used as written, so it must already be encoded. */
  public static MvcTestResult get(MockMvcTester mvc, Session session, String uri) {
    return mvc.get()
        .uri(URI.create(uri))
        .header(HttpHeaders.AUTHORIZATION, session.bearer())
        .exchange();
  }
}

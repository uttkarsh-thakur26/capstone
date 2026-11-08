package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerAs;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.get;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.upload;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.uploaded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * DOC-4 to DOC-6 and ACL-8 beyond the access matrix: list filters and paging, the download
 * response, and a 404 for an unreadable document that cannot be told apart from a missing one.
 */
@IntegrationTest
class DocumentReadTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session faculty;

  @BeforeEach
  void signInAsFaculty() {
    faculty = login(mvc, registerAs(mvc, jdbc, "FACULTY"));
  }

  @Test
  void theListFiltersByTextStatusCategoryAndArchivedState() {
    UUID exam = uploadedWith("Exam rules 100% final", "POLICY");
    UUID fees = uploadedWith("Fee schedule", "OTHER");
    UUID old = uploadedWith("Exam rules 2025", "POLICY");
    setState(fees, "INDEXED", false);
    setState(old, "INDEXED", true);

    assertThat(ids("mine=true&query=EXAM")).containsExactlyInAnyOrder(exam, old);
    // % is matched literally, not as a wildcard.
    assertThat(ids("mine=true&query=100%25")).containsExactly(exam);
    assertThat(ids("mine=true&query=%25%25")).isEmpty();
    assertThat(ids("mine=true&status=INDEXED")).containsExactlyInAnyOrder(fees, old);
    assertThat(ids("mine=true&category=POLICY")).containsExactlyInAnyOrder(exam, old);
    assertThat(ids("mine=true&archived=false")).containsExactlyInAnyOrder(exam, fees);
    assertThat(ids("mine=true&archived=true")).containsExactly(old);
  }

  @Test
  void theListIsPagedAndSortedByAnAllowedColumnOnly() {
    UUID b = uploadedWith("B title", null);
    UUID a = uploadedWith("A title", null);
    UUID c = uploadedWith("C title", null);

    MvcTestResult page = list("mine=true&sort=title,asc&size=2&page=0");
    assertThat(page)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"page": 0, "size": 2, "totalItems": 3, "totalPages": 2}
            """);
    assertThat(idsOf(page)).containsExactly(a, b);
    assertThat(idsOf(list("mine=true&sort=title,asc&size=2&page=1"))).containsExactly(c);
    // Newest first by default.
    assertThat(idsOf(list("mine=true"))).containsExactly(c, a, b);

    MvcTestResult bySecret = list("mine=true&sort=storageKey");
    assertThat(bySecret).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(bySecret).bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT");
  }

  @Test
  void aDownloadStreamsTheFileWithSafeHeadersAndIsAudited() {
    byte[] pdf = TestPdfs.withPages("Download me " + UUID.randomUUID());
    MvcTestResult created =
        upload(
            mvc, faculty, pdf, "Prüfung \"final\".pdf", TestDocuments.metadata("Download", "[]"));
    String id = JsonPath.read(body(created), "$.id");

    MvcTestResult download = get(mvc, faculty, "/api/v1/documents/" + id + "/content");

    assertThat(download).hasStatusOk();
    assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(pdf);
    assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
    assertThat(download.getResponse().getHeaders("X-Content-Type-Options"))
        .containsExactly("nosniff");
    assertThat(download.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("filename*=UTF-8''Pr%C3%BCfung%20%22final%22.pdf")
        .doesNotContain("\r", "\n");
    assertThat(AuditRows.forResource(jdbc, id))
        .extracting(row -> row.get("action"))
        .containsExactly("DOCUMENT_UPLOADED", "DOCUMENT_DOWNLOADED");
  }

  @Test
  void anUnreadableDocumentAnswersExactlyLikeAMissingOne() {
    UUID hidden = uploaded(mvc, faculty, "Hidden", "[]");
    UUID missing = UUID.randomUUID();
    Account student = registerStudent(mvc);
    Session studentSession = login(mvc, student);

    for (String suffix : new String[] {"", "/content"}) {
      MvcTestResult unreadable = get(mvc, studentSession, "/api/v1/documents/" + hidden + suffix);
      MvcTestResult absent = get(mvc, studentSession, "/api/v1/documents/" + missing + suffix);

      assertThat(unreadable).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(absent).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(withoutInstance(body(unreadable))).isEqualTo(withoutInstance(body(absent)));
    }
    // Nothing is recorded for either 404 (ADR-024): only registration and sign-in.
    assertThat(AuditRows.forActorEmail(jdbc, student.email()))
        .extracting(row -> row.get("action"))
        .containsExactly("USER_REGISTERED", "LOGIN_SUCCESS");
  }

  @Test
  void aStoredFileThatIsGoneIsAStorageFailure() throws Exception {
    UUID id = uploaded(mvc, faculty, "Gone", "[]");
    String key =
        jdbc.sql("SELECT storage_key FROM document WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .single();
    Files.delete(Path.of("target/test-files", key));

    MvcTestResult result = get(mvc, faculty, "/api/v1/documents/" + id + "/content");

    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STORAGE_UNAVAILABLE");
  }

  private UUID uploadedWith(String title, String category) {
    MvcTestResult result =
        upload(
            mvc,
            faculty,
            TestPdfs.unique(),
            "a.pdf",
            category == null
                ? "{\"title\": \"%s\"}".formatted(title)
                : "{\"title\": \"%s\", \"category\": \"%s\"}".formatted(title, category));
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(body(result), "$.id"));
  }

  private void setState(UUID id, String status, boolean archived) {
    jdbc.sql("UPDATE document SET status = :status, archived = :archived WHERE id = :id")
        .param("status", status)
        .param("archived", archived)
        .param("id", id)
        .update();
  }

  private MvcTestResult list(String query) {
    MvcTestResult result = get(mvc, faculty, "/api/v1/documents?" + query);
    return result;
  }

  private List<UUID> ids(String query) {
    MvcTestResult result = list(query);
    assertThat(result).hasStatusOk();
    return idsOf(result);
  }

  private static List<UUID> idsOf(MvcTestResult result) {
    List<String> ids = JsonPath.read(body(result), "$.items[*].id");
    return ids.stream().map(UUID::fromString).toList();
  }

  /** The Problem Details body without {@code instance}, which repeats the request path. */
  private static String withoutInstance(String json) {
    return json.replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\",?", "");
  }
}

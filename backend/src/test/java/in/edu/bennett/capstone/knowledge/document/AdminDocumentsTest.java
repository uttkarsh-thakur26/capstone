package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.retrieval.SearchMode;
import in.edu.bennett.capstone.knowledge.retrieval.TestSearch;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Document administration (14.1): the list of every document with its failure reason, and
 * re-indexing everything (15.2 {@code POST /admin/reindex}). Re-indexing sends every document of
 * the database back, so this class has a context and a database of its own, and no other class sees
 * its effect.
 */
@IntegrationTest
@TestPropertySource(properties = "app.test.context=reindex")
class AdminDocumentsTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session admin;
  private Session faculty;

  @BeforeEach
  void signIn() {
    admin = TestAccounts.loginAsAdmin(mvc);
    faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
  }

  @Test
  void theListShowsEveryDocumentWithItsFailureReason() {
    UUID failed = TestDocuments.uploaded(mvc, faculty, "Scanned notice " + UUID.randomUUID(), "[]");
    fail(failed);

    MvcTestResult result =
        TestDocuments.get(mvc, admin, "/api/v1/admin/documents?status=FAILED&size=100");

    assertThat(result).hasStatusOk();
    List<Map<String, Object>> items = JsonPath.read(TestAccounts.body(result), "$.items");
    Map<String, Object> row =
        items.stream().filter(item -> failed.toString().equals(item.get("id"))).findFirst().get();
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(row.get("failureCode")).isEqualTo("NO_TEXT_LAYER");
    assertThat(row.get("failureMessage")).isEqualTo("The PDF has no text layer.");
    assertThat(items).allMatch(item -> "FAILED".equals(item.get("status")));
  }

  @Test
  void reindexingSendsIndexedFailedAndArchivedDocumentsBack() {
    String marker = "reindexmarker" + UUID.randomUUID().toString().replace("-", "");
    UUID indexed = indexed(mvc, jdbc, faculty, "Rules " + marker, "[]", 20, marker + " rules");
    UUID archived = indexed(mvc, jdbc, faculty, "Old rules " + marker, "[]", 20, "old rules");
    jdbc.sql("UPDATE document SET archived = true WHERE id = :id").param("id", archived).update();
    UUID failed = TestDocuments.uploaded(mvc, faculty, "Scanned " + marker, "[]");
    fail(failed);
    UUID waiting = TestDocuments.uploaded(mvc, faculty, "Waiting " + marker, "[]");
    assertThat(TestSearch.hits(mvc, faculty, SearchMode.KEYWORD, marker, null)).hasSize(1);
    long others =
        jdbc.sql("SELECT count(*) FROM document WHERE status IN ('INDEXED', 'FAILED')")
            .query(Long.class)
            .single();

    MvcTestResult result =
        TestDocuments.send(mvc, admin, HttpMethod.POST, "/api/v1/admin/reindex", null);

    assertThat(result).hasStatus(HttpStatus.ACCEPTED);
    String body = TestAccounts.body(result);
    assertThat((Integer) JsonPath.read(body, "$.queued")).isEqualTo((int) others);
    for (UUID id : List.of(indexed, archived, failed, waiting)) {
      Map<String, Object> row =
          jdbc.sql(
                  "SELECT status, failure_code, processing_attempts, indexed_at FROM document"
                      + " WHERE id = :id")
              .param("id", id)
              .query()
              .singleRow();
      assertThat(row.get("status")).as("status of %s", id).isEqualTo("UPLOADED");
      assertThat(row.get("failure_code")).isNull();
      assertThat(row.get("processing_attempts")).isEqualTo(0);
      assertThat(row.get("indexed_at")).isNull();
    }
    // Until it is processed again, it is out of search (ACL-4).
    assertThat(TestSearch.hits(mvc, faculty, SearchMode.KEYWORD, marker, null)).isEmpty();
    String details =
        jdbc.sql(
                "SELECT details::text FROM audit_log WHERE action = 'REINDEX_STARTED'"
                    + " AND actor_email = :email ORDER BY id DESC LIMIT 1")
            .param("email", TestAccounts.ADMIN_EMAIL)
            .query(String.class)
            .single();
    assertThat(details).contains("\"queued\": " + others);
  }

  private void fail(UUID id) {
    jdbc.sql(
            "UPDATE document SET status = 'FAILED', failure_code = 'NO_TEXT_LAYER',"
                + " failure_message = 'The PDF has no text layer.', processing_attempts = 1"
                + " WHERE id = :id")
        .param("id", id)
        .update();
  }
}

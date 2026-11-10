package in.edu.bennett.capstone.knowledge.audit;

import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.awaitFinished;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.compare;
import static in.edu.bennett.capstone.knowledge.assistant.TestSummaries.indexed;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import in.edu.bennett.capstone.knowledge.assistant.TestSummaries;
import in.edu.bennett.capstone.knowledge.conversation.TestConversations;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Phase 9 exit criterion: every action of 14.2 produces an audit entry, and no entry holds a secret
 * or document content (AUD-2). Each action is performed through the real endpoints; then every
 * entry written meanwhile is read back and searched for the passwords, tokens, document text and
 * question that were used. Re-indexing touches every document, so this class shares the isolated
 * context of {@code AdminDocumentsTest}.
 */
@IntegrationTest
@TestPropertySource(properties = "app.test.context=reindex")
class AuditCoverageTest {

  private static final String NEW_PASSWORD = "my second passphrase";
  private static final String RESET_PASSWORD = "set by the admin today";
  private static final String WRONG_PASSWORD = "certainly not my password";
  private static final String DOCUMENT_TEXT = "Zephyrine lockers open at 07:15 every weekday.";
  private static final String QUESTION = "When do zephyrine lockers open every weekday?";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void everyActionIsRecordedWithoutSecretsOrContent() {
    OffsetDateTime start =
        jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single();
    Session admin = TestAccounts.loginAsAdmin(mvc);

    // USER_REGISTERED, LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT
    Account student = TestAccounts.registerStudent(mvc);
    Session studentSession = TestAccounts.login(mvc, student);
    assertThat(TestAccounts.loginRequest(mvc, student.email(), WRONG_PASSWORD))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    Session ended = TestAccounts.login(mvc, student);
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, TestAccounts.ORIGIN)
                .cookie(ended.refreshCookie())
                .exchange())
        .hasStatus(HttpStatus.NO_CONTENT);

    // ROLE_CHANGED, USER_DISABLED, USER_ENABLED, PASSWORD_RESET
    Account teacher = TestAccounts.registerStudent(mvc);
    assertThat(patchUser(admin, teacher.id(), "{\"role\": \"FACULTY\"}")).hasStatusOk();
    Account other = TestAccounts.registerStudent(mvc);
    assertThat(patchUser(admin, other.id(), "{\"enabled\": false}")).hasStatusOk();
    assertThat(patchUser(admin, other.id(), "{\"enabled\": true}")).hasStatusOk();
    assertThat(
            TestDocuments.send(
                mvc,
                admin,
                HttpMethod.POST,
                "/api/v1/admin/users/" + other.id() + "/password",
                "{\"newPassword\": \"" + RESET_PASSWORD + "\"}"))
        .hasStatus(HttpStatus.NO_CONTENT);
    Session faculty = TestAccounts.login(mvc, teacher);

    // DOCUMENT_UPLOADED, DOCUMENT_DOWNLOADED, DOCUMENT_UPDATED, DOCUMENT_ARCHIVED, GRANTS_CHANGED
    MvcTestResult uploaded =
        TestDocuments.upload(
            mvc,
            faculty,
            TestPdfs.withPageLines(List.of(List.of(DOCUMENT_TEXT))),
            "lockers.pdf",
            TestDocuments.metadata("Locker notice", "[]"));
    assertThat(uploaded).hasStatus(HttpStatus.CREATED);
    UUID document = UUID.fromString(JsonPath.read(TestAccounts.body(uploaded), "$.id"));
    assertThat(TestDocuments.get(mvc, faculty, "/api/v1/documents/" + document + "/content"))
        .hasStatusOk();
    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                HttpMethod.PATCH,
                "/api/v1/documents/" + document,
                "{\"title\": \"Locker notice, revised\"}"))
        .hasStatusOk();
    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                HttpMethod.PATCH,
                "/api/v1/documents/" + document,
                "{\"archived\": true}"))
        .hasStatusOk();
    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                HttpMethod.PUT,
                "/api/v1/documents/" + document + "/grants",
                "{\"grants\": [{\"type\": \"ROLE\", \"role\": \"STUDENT\"}]}"))
        .hasStatusOk();

    // DOCUMENT_REPROCESSED: only an indexed or failed document can be processed again.
    jdbc.sql("UPDATE document SET status = 'INDEXED', indexed_at = now() WHERE id = :id")
        .param("id", document)
        .update();
    assertThat(
            TestDocuments.send(
                mvc,
                faculty,
                HttpMethod.POST,
                "/api/v1/documents/" + document + "/reprocess",
                null))
        .hasStatusOk();

    // ACCESS_DENIED
    assertThat(TestDocuments.get(mvc, studentSession, "/api/v1/admin/users"))
        .hasStatus(HttpStatus.FORBIDDEN);

    // AI_QUERY, SUMMARY_REQUESTED, COMPARISON_REQUESTED
    UUID left = indexed(mvc, jdbc, faculty, "Lockers A", "[]", 20, DOCUMENT_TEXT);
    UUID right =
        indexed(
            mvc,
            jdbc,
            faculty,
            "Lockers B",
            "[]",
            20,
            "Zephyrine lockers open at 08:00 every weekday.");
    assertThat(TestConversations.answer(mvc, faculty, QUESTION).outcome()).isEqualTo("ANSWERED");
    assertThat(TestSummaries.request(mvc, faculty, left)).hasStatus(HttpStatus.ACCEPTED);
    awaitFinished(mvc, faculty, left);
    assertThat(compare(mvc, faculty, left, right)).hasStatusOk();

    // PASSWORD_CHANGED
    assertThat(
            mvc.put()
                .uri("/api/v1/auth/me/password")
                .header(HttpHeaders.AUTHORIZATION, studentSession.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                        .formatted(TestAccounts.PASSWORD, NEW_PASSWORD))
                .exchange())
        .hasStatus(HttpStatus.NO_CONTENT);

    // REINDEX_STARTED, DOCUMENT_DELETED
    assertThat(TestDocuments.send(mvc, admin, HttpMethod.POST, "/api/v1/admin/reindex", null))
        .hasStatus(HttpStatus.ACCEPTED);
    assertThat(
            TestDocuments.send(
                mvc, faculty, HttpMethod.DELETE, "/api/v1/documents/" + document, null))
        .hasStatus(HttpStatus.NO_CONTENT);

    List<Map<String, Object>> entries =
        jdbc.sql(
                """
                SELECT action, outcome, actor_email, resource_type, resource_id,
                       host(ip_address) AS ip, details::text AS details
                FROM audit_log WHERE occurred_at >= :start ORDER BY id
                """)
            .param("start", start)
            .query()
            .listOfRows();

    Set<AuditAction> recorded =
        entries.stream()
            .map(row -> AuditAction.valueOf((String) row.get("action")))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(AuditAction.class)));
    assertThat(recorded).containsExactlyInAnyOrder(AuditAction.values());

    List<String> secrets =
        List.of(
            TestAccounts.PASSWORD,
            NEW_PASSWORD,
            RESET_PASSWORD,
            WRONG_PASSWORD,
            TestAccounts.ADMIN_PASSWORD,
            admin.accessToken(),
            admin.refreshToken(),
            studentSession.accessToken(),
            studentSession.refreshToken(),
            faculty.accessToken(),
            faculty.refreshToken(),
            "Zephyrine",
            "zephyrine",
            "07:15",
            QUESTION);
    for (Map<String, Object> entry : entries) {
      String text = entry.values().toString();
      for (String secret : secrets) {
        assertThat(text)
            .as("audit entry %s must not hold %s", entry.get("action"), secret)
            .doesNotContain(secret);
      }
    }
    assertThat(Arrays.asList(AuditAction.values())).hasSize(21);
  }

  private MvcTestResult patchUser(Session admin, UUID id, String json) {
    return TestDocuments.send(mvc, admin, HttpMethod.PATCH, "/api/v1/admin/users/" + id, json);
  }
}

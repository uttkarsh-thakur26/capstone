package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginAsAdmin;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerAs;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.get;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.send;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.uploaded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * DOC-7 to DOC-11 and ACL-2 with ADR-030: who may edit, archive, reprocess and delete; that delete
 * removes the rows and the stored file (19.2); that illegal status transitions are refused (19.2);
 * and that a demoted owner keeps read access but loses manage rights.
 */
@IntegrationTest
class DocumentManagementTest {

  private static final String STUDENT_GRANT = "[{\"type\": \"ROLE\", \"role\": \"STUDENT\"}]";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Account owner;
  private Session ownerSession;

  @BeforeEach
  void signInAsFaculty() {
    owner = registerAs(mvc, jdbc, "FACULTY");
    ownerSession = login(mvc, owner);
  }

  @Test
  void theOwnerReplacesTheMetadataAsASet() {
    UUID id = uploadedWithMetadata();

    MvcTestResult result =
        send(
            mvc,
            ownerSession,
            HttpMethod.PATCH,
            "/api/v1/documents/" + id,
            """
            {"title": " New title ", "category": "Rules"}
            """);

    assertThat(result).hasStatusOk();
    assertThat(result)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title": "New title", "category": "Rules", "description": null,
             "documentDate": null, "canManage": true}
            """);
    assertThat(actions(id)).containsExactly("DOCUMENT_UPLOADED", "DOCUMENT_UPDATED");
    assertThat(AuditRows.forResource(jdbc, id.toString()).getLast().get("details").toString())
        .contains("title", "description", "category", "documentDate");
  }

  @Test
  void anAdminManagesAnyonesDocument() {
    UUID id = uploaded(mvc, ownerSession, "By faculty", "[]");

    MvcTestResult result =
        send(
            mvc,
            loginAsAdmin(mvc),
            HttpMethod.PATCH,
            "/api/v1/documents/" + id,
            "{\"title\": \"Renamed by admin\"}");

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Renamed by admin");
  }

  @Test
  void aReaderWhoCannotManageGets403AndTheRefusalIsAudited() {
    UUID id = uploaded(mvc, ownerSession, "Shared", STUDENT_GRANT);
    Account student = registerStudent(mvc);
    Session studentSession = login(mvc, student);

    assertThat(get(mvc, studentSession, "/api/v1/documents/" + id))
        .bodyJson()
        .extractingPath("$.canManage")
        .isEqualTo(false);
    for (MvcTestResult refused :
        List.of(
            send(
                mvc,
                studentSession,
                HttpMethod.PATCH,
                "/api/v1/documents/" + id,
                "{\"archived\": true}"),
            send(
                mvc,
                studentSession,
                HttpMethod.POST,
                "/api/v1/documents/" + id + "/reprocess",
                null),
            send(mvc, studentSession, HttpMethod.DELETE, "/api/v1/documents/" + id, null))) {
      assertThat(refused).hasStatus(HttpStatus.FORBIDDEN);
      assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    assertThat(AuditRows.forActorEmail(jdbc, student.email()))
        .filteredOn(row -> "ACCESS_DENIED".equals(row.get("action")))
        .extracting(row -> row.get("resource_id"))
        .containsExactly(id.toString(), id.toString(), id.toString());
    assertThat(documentExists(id)).isTrue();
  }

  @Test
  void someoneWhoCannotReadItGets404AndNothingIsRecorded() {
    UUID id = uploaded(mvc, ownerSession, "Private", "[]");
    Account otherFaculty = registerAs(mvc, jdbc, "FACULTY");
    Session other = login(mvc, otherFaculty);

    assertThat(
            send(mvc, other, HttpMethod.PATCH, "/api/v1/documents/" + id, "{\"archived\": true}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(send(mvc, other, HttpMethod.DELETE, "/api/v1/documents/" + id, null))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(AuditRows.forActorEmail(jdbc, otherFaculty.email()))
        .extracting(row -> row.get("action"))
        .containsExactly("USER_REGISTERED", "LOGIN_SUCCESS");
  }

  @Test
  void anArchivedDocumentStaysReadableAndCanBeBroughtBack() {
    UUID id = uploaded(mvc, ownerSession, "Superseded rules", STUDENT_GRANT);
    Session student = login(mvc, registerStudent(mvc));

    assertThat(patch(id, "{\"archived\": true}"))
        .bodyJson()
        .extractingPath("$.archived")
        .isEqualTo(true);
    assertThat(get(mvc, student, "/api/v1/documents/" + id)).hasStatusOk();
    assertThat(get(mvc, student, "/api/v1/documents/" + id + "/content")).hasStatusOk();
    assertThat(patch(id, "{\"archived\": false}"))
        .bodyJson()
        .extractingPath("$.archived")
        .isEqualTo(false);

    List<Map<String, Object>> entries = AuditRows.forResource(jdbc, id.toString());
    assertThat(entries)
        .filteredOn(row -> "DOCUMENT_ARCHIVED".equals(row.get("action")))
        .extracting(row -> row.get("details").toString())
        .containsExactly("{\"archived\": true}", "{\"archived\": false}");
  }

  @Test
  void anUpdateMustSayWhatItChanges() {
    UUID id = uploaded(mvc, ownerSession, "Doc", "[]");

    assertThat(patch(id, "{}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(patch(id, "{\"description\": \"no title\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(patch(id, "{\"title\": \"   \"}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(actions(id)).containsExactly("DOCUMENT_UPLOADED");
  }

  /**
   * A title of Unicode spaces (here an em space and an en space) is blank too: the title is
   * stripped before it is stored, so it must be refused as a 400, not reach the database's
   * not-blank check and fail there (15.1).
   */
  @Test
  void aTitleOfUnicodeSpacesIsRefusedAsBlank() {
    UUID id = uploaded(mvc, ownerSession, "Doc", "[]");

    MvcTestResult result = patch(id, "{\"title\": \"\\u2003\\u2002\"}");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
    assertThat(actions(id)).containsExactly("DOCUMENT_UPLOADED");
  }

  @Test
  void reprocessSendsAFinishedDocumentBackWithAFreshCounter() {
    UUID failed = uploaded(mvc, ownerSession, "Failed", "[]");
    UUID indexed = uploaded(mvc, ownerSession, "Indexed", "[]");
    jdbc.sql(
            """
            UPDATE document SET status = 'FAILED', failure_code = 'EXTRACTION_ERROR',
                                processing_attempts = 3
            WHERE id = :id
            """)
        .param("id", failed)
        .update();
    jdbc.sql("UPDATE document SET status = 'INDEXED' WHERE id = :id").param("id", indexed).update();

    MvcTestResult again = reprocess(failed);

    assertThat(again).hasStatusOk();
    assertThat(again)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": "UPLOADED", "failureCode": null, "failureMessage": null}
            """);
    assertThat(
            jdbc.sql("SELECT processing_attempts FROM document WHERE id = :id")
                .param("id", failed)
                .query(Integer.class)
                .single())
        .isZero();
    assertThat(reprocess(indexed)).bodyJson().extractingPath("$.status").isEqualTo("UPLOADED");
    assertThat(AuditRows.forResource(jdbc, failed.toString()).getLast())
        .containsEntry("action", "DOCUMENT_REPROCESSED")
        .containsEntry("details", "{\"from\": \"FAILED\"}");
  }

  @Test
  void aDocumentThatIsQueuedOrRunningCannotBeReprocessed() {
    UUID uploaded = uploaded(mvc, ownerSession, "Queued", "[]");
    UUID processing = uploaded(mvc, ownerSession, "Running", "[]");
    jdbc.sql(
            """
            UPDATE document SET status = 'PROCESSING', processing_run_id = gen_random_uuid(),
                processing_heartbeat_at = now() WHERE id = :id
            """)
        .param("id", processing)
        .update();

    for (UUID id : List.of(uploaded, processing)) {
      MvcTestResult refused = reprocess(id);
      assertThat(refused).hasStatus(HttpStatus.CONFLICT);
      assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("INVALID_STATUS");
    }
    assertThat(actions(uploaded)).containsExactly("DOCUMENT_UPLOADED");
  }

  @Test
  void deleteRemovesTheRowsAndTheStoredFile() {
    Account student = registerStudent(mvc);
    UUID id =
        uploaded(
            mvc,
            ownerSession,
            "To delete",
            "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(student.id()));
    String key = storageKey(id);
    assertThat(Path.of("target/test-files", key)).exists();

    MvcTestResult deleted =
        send(mvc, ownerSession, HttpMethod.DELETE, "/api/v1/documents/" + id, null);

    assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(documentExists(id)).isFalse();
    assertThat(count("SELECT count(*) FROM document_grant WHERE document_id = :id", id)).isZero();
    assertThat(Path.of("target/test-files", key)).doesNotExist();
    assertThat(
            jdbc.sql("SELECT count(*) FROM storage_deletion WHERE storage_key = :key")
                .param("key", key)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(actions(id)).containsExactly("DOCUMENT_UPLOADED", "DOCUMENT_DELETED");
    assertThat(get(mvc, ownerSession, "/api/v1/documents/" + id)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aDemotedOwnerStillReadsTheDocumentButNoLongerManagesIt() {
    UUID id = uploaded(mvc, ownerSession, "Mine", "[]");
    jdbc.sql("UPDATE app_user SET role = 'STUDENT' WHERE id = :id")
        .param("id", owner.id())
        .update();

    // The next request already sees the new role (AUTH-7).
    assertThat(get(mvc, ownerSession, "/api/v1/documents/" + id))
        .bodyJson()
        .extractingPath("$.canManage")
        .isEqualTo(false);
    assertThat(patch(id, "{\"archived\": true}")).hasStatus(HttpStatus.FORBIDDEN);
  }

  private UUID uploadedWithMetadata() {
    MvcTestResult result =
        TestDocuments.upload(
            mvc,
            ownerSession,
            TestPdfs.unique(),
            "a.pdf",
            """
            {"title": "Old title", "description": "Old", "category": "Old",
             "documentDate": "2026-01-01"}
            """);
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(JsonPath.read(body(result), "$.id"));
  }

  private MvcTestResult patch(UUID id, String json) {
    return send(mvc, ownerSession, HttpMethod.PATCH, "/api/v1/documents/" + id, json);
  }

  private MvcTestResult reprocess(UUID id) {
    return send(mvc, ownerSession, HttpMethod.POST, "/api/v1/documents/" + id + "/reprocess", null);
  }

  private List<Object> actions(UUID id) {
    return AuditRows.forResource(jdbc, id.toString()).stream()
        .map(row -> row.get("action"))
        .toList();
  }

  private boolean documentExists(UUID id) {
    return count("SELECT count(*) FROM document WHERE id = :id", id) == 1;
  }

  private long count(String sql, UUID id) {
    return jdbc.sql(sql).param("id", id).query(Long.class).single();
  }

  private String storageKey(UUID id) {
    return jdbc.sql("SELECT storage_key FROM document WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }
}

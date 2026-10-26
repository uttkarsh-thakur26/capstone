package in.edu.bennett.capstone.knowledge.document;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
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

/** DOC-8, ACL-2, ACL-5 and ACL-7: reading and replacing grants, with immediate effect. */
@IntegrationTest
class DocumentGrantsTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session owner;
  private Account student;
  private Session studentSession;

  @BeforeEach
  void signIn() {
    owner = login(mvc, registerAs(mvc, jdbc, "FACULTY"));
    student = registerStudent(mvc);
    studentSession = login(mvc, student);
  }

  @Test
  void theOwnerSeesRoleGrantsFirstThenPeopleWithTheirNames() {
    UUID id =
        uploaded(
            mvc,
            owner,
            "Shared",
            """
            [{"type": "USER", "userId": "%s"}, {"type": "ROLE", "role": "STUDENT"}]
            """
                .formatted(student.id()));

    assertThat(get(mvc, owner, grantsOf(id)))
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"grants": [
              {"type": "ROLE", "role": "STUDENT", "user": null},
              {"type": "USER", "role": null,
               "user": {"id": "%s", "displayName": "Test User", "email": "%s"}}]}
            """
                .formatted(student.id(), student.email()));
  }

  @Test
  void aNewGrantAndARevokedGrantApplyToTheNextRequest() {
    UUID id = uploaded(mvc, owner, "Exam policy " + UUID.randomUUID(), "[]");
    assertThat(get(mvc, studentSession, "/api/v1/documents/" + id)).hasStatus(HttpStatus.NOT_FOUND);

    assertThat(replace(id, "[{\"type\": \"ROLE\", \"role\": \"STUDENT\"}]")).hasStatusOk();
    assertThat(get(mvc, studentSession, "/api/v1/documents/" + id)).hasStatusOk();
    assertThat(get(mvc, studentSession, "/api/v1/documents/" + id + "/content")).hasStatusOk();
    assertThat(listedForStudent(id)).isTrue();

    assertThat(replace(id, "[]")).bodyJson().isLenientlyEqualTo("{\"grants\": []}");
    assertThat(get(mvc, studentSession, "/api/v1/documents/" + id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(mvc, studentSession, "/api/v1/documents/" + id + "/content"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(listedForStudent(id)).isFalse();
  }

  @Test
  void aChangeIsAuditedWithTheSetsBeforeAndAfterAndANoChangeIsNot() {
    UUID id = uploaded(mvc, owner, "Audited", "[{\"type\": \"ROLE\", \"role\": \"FACULTY\"}]");

    replace(
        id,
        """
        [{"type": "ROLE", "role": "STUDENT"}, {"type": "USER", "userId": "%s"}]
        """
            .formatted(student.id()));
    replace(
        id,
        """
        [{"type": "USER", "userId": "%s"}, {"type": "ROLE", "role": "STUDENT"}]
        """
            .formatted(student.id()));

    List<Map<String, Object>> changes =
        AuditRows.forResource(jdbc, id.toString()).stream()
            .filter(row -> "GRANTS_CHANGED".equals(row.get("action")))
            .toList();
    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().get("details").toString())
        .contains("\"before\": [\"ROLE:FACULTY\"]")
        .contains("\"after\": [\"ROLE:STUDENT\", \"USER:" + student.id() + "\"]");
  }

  @Test
  void invalidGrantsAreRejectedAndTheOldSetStays() {
    UUID id = uploaded(mvc, owner, "Kept", "[{\"type\": \"ROLE\", \"role\": \"STUDENT\"}]");

    assertThat(replace(id, "[{\"type\": \"ROLE\", \"role\": \"ADMIN\"}]"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            replace(id, "[{\"type\": \"USER\", \"userId\": \"%s\"}]".formatted(UUID.randomUUID())))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(send(mvc, owner, HttpMethod.PUT, grantsOf(id), "{}"))
        .hasStatus(HttpStatus.BAD_REQUEST);

    assertThat(get(mvc, owner, grantsOf(id)))
        .bodyJson()
        .extractingPath("$.grants[*].role")
        .asArray()
        .containsExactly("STUDENT");
  }

  @Test
  void onlyTheOwnerAndAdminsSeeOrChangeGrants() {
    UUID shared = uploaded(mvc, owner, "Readable", "[{\"type\": \"ROLE\", \"role\": \"STUDENT\"}]");
    UUID hidden = uploaded(mvc, owner, "Hidden", "[]");

    assertThat(get(mvc, studentSession, grantsOf(shared))).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(send(mvc, studentSession, HttpMethod.PUT, grantsOf(shared), "{\"grants\": []}"))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(get(mvc, studentSession, grantsOf(hidden))).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(send(mvc, studentSession, HttpMethod.PUT, grantsOf(hidden), "{\"grants\": []}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(mvc, owner, grantsOf(shared)))
        .bodyJson()
        .extractingPath("$.grants")
        .asArray()
        .hasSize(1);
  }

  private MvcTestResult replace(UUID id, String grantsJson) {
    return send(mvc, owner, HttpMethod.PUT, grantsOf(id), "{\"grants\": %s}".formatted(grantsJson));
  }

  private boolean listedForStudent(UUID id) {
    List<String> ids =
        JsonPath.read(
            body(get(mvc, studentSession, "/api/v1/documents?size=100")), "$.items[*].id");
    return ids.contains(id.toString());
  }

  private static String grantsOf(UUID id) {
    return "/api/v1/documents/" + id + "/grants";
  }
}

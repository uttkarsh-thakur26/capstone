package in.edu.bennett.capstone.knowledge.user;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
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
 * An admin sets a new password (14.1 {@code [T2]}, 15.2 {@code POST /admin/users/{id}/password}):
 * the new one works, the old one does not, every session of the account ends, and the {@code
 * PASSWORD_RESET} entry names the admin and holds no password (AUD-2).
 */
@IntegrationTest
class PasswordResetTest {

  private static final String NEW_PASSWORD = "a brand new passphrase";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session admin;
  private Account student;

  @BeforeEach
  void setUp() {
    admin = TestAccounts.loginAsAdmin(mvc);
    student = TestAccounts.registerStudent(mvc);
  }

  @Test
  void theNewPasswordWorksTheOldOneNoLongerAndSessionsEnd() {
    Session before = TestAccounts.login(mvc, student);

    assertThat(reset(admin, student.id(), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(TestAccounts.loginRequest(mvc, student.email(), TestAccounts.PASSWORD))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(TestAccounts.loginRequest(mvc, student.email(), NEW_PASSWORD)).hasStatusOk();
    assertThat(TestAccounts.refresh(mvc, before.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theAuditEntryNamesTheAdminAndHoldsNoPassword() {
    assertThat(reset(admin, student.id(), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

    List<Map<String, Object>> entries =
        AuditRows.forResource(jdbc, student.id().toString()).stream()
            .filter(row -> "PASSWORD_RESET".equals(row.get("action")))
            .toList();
    assertThat(entries).hasSize(1);
    Map<String, Object> entry = entries.getFirst();
    assertThat(entry.get("outcome")).isEqualTo("SUCCESS");
    assertThat(entry.get("actor_email")).isEqualTo(TestAccounts.ADMIN_EMAIL);
    assertThat(entry.get("resource_type")).isEqualTo("USER");
    assertThat(entry.toString()).doesNotContain(NEW_PASSWORD);
  }

  @Test
  void anAdminCannotResetTheirOwnPasswordHere() {
    UUID adminId =
        jdbc.sql("SELECT id FROM app_user WHERE email = :email")
            .param("email", TestAccounts.ADMIN_EMAIL)
            .query(UUID.class)
            .single();

    MvcTestResult result = reset(admin, adminId, NEW_PASSWORD);

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("OWN_ACCOUNT");
  }

  @Test
  void aShortPasswordAnUnknownUserAndANonAdminAreRefused() {
    MvcTestResult tooShort = reset(admin, student.id(), "short");
    assertThat(tooShort).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(tooShort).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");

    assertThat(reset(admin, UUID.randomUUID(), NEW_PASSWORD)).hasStatus(HttpStatus.NOT_FOUND);

    Session faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));
    assertThat(reset(faculty, student.id(), NEW_PASSWORD)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(TestAccounts.loginRequest(mvc, student.email(), TestAccounts.PASSWORD))
        .hasStatusOk();
  }

  private MvcTestResult reset(Session session, UUID userId, String password) {
    return TestDocuments.send(
        mvc,
        session,
        HttpMethod.POST,
        "/api/v1/admin/users/" + userId + "/password",
        "{\"newPassword\": \"" + password + "\"}");
  }
}

package in.edu.bennett.capstone.knowledge.security;

import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginRequest;
import static in.edu.bennett.capstone.knowledge.TestAccounts.me;
import static in.edu.bennett.capstone.knowledge.TestAccounts.refresh;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The account's current state wins over what a token says (AUTH-7): the 19.2 test that a disabled
 * user is rejected, and changing one's own password (S2, ADR-025).
 */
@IntegrationTest
class AccountStateTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void aDisabledUserIsRejectedOnTheNextRequestAtRefreshAndAtSignIn() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);
    assertThat(me(mvc, session.accessToken())).hasStatusOk();

    jdbc.sql("UPDATE app_user SET enabled = false WHERE id = :id")
        .param("id", account.id())
        .update();

    // The access token is still unexpired; the database decides.
    assertThat(me(mvc, session.accessToken())).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(mvc, session.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(loginRequest(mvc, account.email(), PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void changingThePasswordNeedsTheCurrentOneAndEndsEverySession() {
    Account account = registerStudent(mvc);
    Session first = login(mvc, account.email(), PASSWORD);
    Session second = login(mvc, account.email(), PASSWORD);

    MvcTestResult result = changePassword(first, PASSWORD, "a brand new passphrase");

    assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    assertThat(refresh(mvc, first.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(refresh(mvc, second.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(loginRequest(mvc, account.email(), PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(loginRequest(mvc, account.email(), "a brand new passphrase")).hasStatusOk();
    assertThat(AuditRows.forResource(jdbc, account.id().toString()))
        .filteredOn(row -> "PASSWORD_CHANGED".equals(row.get("action")))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("outcome", "SUCCESS"));
  }

  @Test
  void aWrongCurrentPasswordChangesNothingAndIsRecorded() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    MvcTestResult result = changePassword(session, "not my password", "a brand new passphrase");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CURRENT_PASSWORD_INCORRECT");
    assertThat(loginRequest(mvc, account.email(), PASSWORD)).hasStatusOk();
    assertThat(refresh(mvc, session.refreshToken())).hasStatusOk();
    assertThat(AuditRows.forResource(jdbc, account.id().toString()))
        .filteredOn(row -> "PASSWORD_CHANGED".equals(row.get("action")))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("outcome", "DENIED"));
  }

  @Test
  void theNewPasswordFollowsTheLengthRule() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    MvcTestResult result = changePassword(session, PASSWORD, "too short");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("newPassword");
  }

  private MvcTestResult changePassword(Session session, String current, String next) {
    return mvc.put()
        .uri("/api/v1/auth/me/password")
        .header(HttpHeaders.AUTHORIZATION, session.bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"currentPassword": "%s", "newPassword": "%s"}
            """
                .formatted(current, next))
        .exchange();
  }
}

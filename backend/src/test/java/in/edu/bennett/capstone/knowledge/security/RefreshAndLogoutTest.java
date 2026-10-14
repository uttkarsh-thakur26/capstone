package in.edu.bennett.capstone.knowledge.security;

import static in.edu.bennett.capstone.knowledge.TestAccounts.ORIGIN;
import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.me;
import static in.edu.bennett.capstone.knowledge.TestAccounts.refresh;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static in.edu.bennett.capstone.knowledge.TestAccounts.session;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Refresh and sign-out: AUTH-6, AUTH-9, ADR-020, and the 19.2 test that refresh rotates the token
 * and a reused old token is rejected.
 */
@IntegrationTest
class RefreshAndLogoutTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void refreshRotatesTheTokenAndIssuesANewAccessToken() {
    Account account = registerStudent(mvc);
    Session first = login(mvc, account.email(), PASSWORD);

    MvcTestResult result = refresh(mvc, first.refreshToken());

    assertThat(result).hasStatusOk();
    Session second = session(result);
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(result).bodyJson().extractingPath("$.user.id").isEqualTo(account.id().toString());
    assertThat(me(mvc, second.accessToken())).hasStatusOk();
  }

  @Test
  void aReusedRefreshTokenIsRejectedAndEndsTheWholeSignIn() {
    Account account = registerStudent(mvc);
    Session first = login(mvc, account.email(), PASSWORD);
    Session second = session(refresh(mvc, first.refreshToken()));

    MvcTestResult reuse = refresh(mvc, first.refreshToken());

    assertSessionExpired(reuse);
    // Someone holds a copy of the first token, so the token rotated from it is revoked too.
    assertSessionExpired(refresh(mvc, second.refreshToken()));
  }

  @Test
  void anotherSignInIsNotAffectedByReuseInTheFirst() {
    Account account = registerStudent(mvc);
    Session phone = login(mvc, account.email(), PASSWORD);
    Session laptop = login(mvc, account.email(), PASSWORD);
    refresh(mvc, phone.refreshToken());

    refresh(mvc, phone.refreshToken());

    assertThat(refresh(mvc, laptop.refreshToken())).hasStatusOk();
  }

  @Test
  void refreshWithoutACookieIsRejected() {
    assertSessionExpired(
        mvc.post().uri("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).exchange());
  }

  @Test
  void anExpiredRefreshTokenIsRejected() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);
    jdbc.sql(
            "UPDATE refresh_token SET expires_at = now() - interval '1 minute' WHERE user_id = :id")
        .param("id", account.id())
        .update();

    assertSessionExpired(refresh(mvc, session.refreshToken()));
  }

  @Test
  void refreshFromAnotherOriginIsRefusedAndLeavesTheTokenValid() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/refresh")
            .header(HttpHeaders.ORIGIN, "https://evil.example")
            .cookie(session.refreshCookie())
            .exchange();

    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ORIGIN_NOT_ALLOWED");
    assertThat(refresh(mvc, session.refreshToken())).hasStatusOk();
  }

  @Test
  void anExpiredAccessTokenSentAlongDoesNotBlockTheRefresh() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/refresh")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer no.longer.valid")
            .cookie(session.refreshCookie())
            .exchange();

    assertThat(result).hasStatusOk();
  }

  @Test
  void logoutNeedsOnlyTheCookieAndEndsTheSignIn() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    // No access token: logout must work after it expired (ADR-020).
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/logout")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .cookie(session.refreshCookie())
            .exchange();

    assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .startsWith("refresh_token=;")
        .contains("Max-Age=0");
    assertSessionExpired(refresh(mvc, session.refreshToken()));
    assertThat(AuditRows.forResource(jdbc, account.id().toString()))
        .extracting(row -> row.get("action"))
        .contains("LOGOUT");
  }

  @Test
  void logoutWithoutACookieStillSucceeds() {
    assertThat(mvc.post().uri("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN).exchange())
        .hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void logoutFromAnotherOriginIsRefused() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/logout")
            .header(HttpHeaders.ORIGIN, "https://evil.example")
            .cookie(session.refreshCookie())
            .exchange();

    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(refresh(mvc, session.refreshToken())).hasStatusOk();
  }

  @Test
  void theDatabaseHoldsOnlyAHashOfTheToken() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    String stored =
        jdbc.sql("SELECT token_hash FROM refresh_token WHERE user_id = :id")
            .param("id", account.id())
            .query(String.class)
            .single();

    assertThat(stored)
        .isNotEqualTo(session.refreshToken())
        .isEqualTo(RefreshTokenService.hash(session.refreshToken()))
        .hasSize(64);
  }

  private static void assertSessionExpired(MvcTestResult result) {
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SESSION_EXPIRED");
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }
}

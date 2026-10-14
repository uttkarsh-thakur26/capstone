package in.edu.bennett.capstone.knowledge.security;

import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginRequest;
import static in.edu.bennett.capstone.knowledge.TestAccounts.me;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static in.edu.bennett.capstone.knowledge.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Sign-in: AUTH-5, AUTH-6, AUTH-10, and the 19.2 test that failures look the same. */
@IntegrationTest
class LoginTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void signInReturnsAnAccessTokenAndSetsTheRefreshCookie() {
    Account account = registerStudent(mvc);

    MvcTestResult result = loginRequest(mvc, account.email(), PASSWORD);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
    assertThat(result).bodyJson().extractingPath("$.user.role").isEqualTo("STUDENT");
    assertThat(result).bodyJson().extractingPath("$.user.email").isEqualTo(account.email());
    Instant expiresAt = Instant.parse(JsonPath.read(body(result), "$.expiresAt"));
    assertThat(Duration.between(Instant.now(), expiresAt).toSeconds())
        .isCloseTo(15 * 60, within(5L));
    // The refresh token travels only in the cookie, never in the body.
    assertThat(body(result)).doesNotContain(SetCookie.value(result));

    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie)
        .startsWith("refresh_token=")
        .contains("Path=/api/v1/auth")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");
    assertThat(SetCookie.maxAge(cookie)).isCloseTo(7 * 24 * 3600, within(5L));
  }

  @Test
  void theAccessTokenIdentifiesTheUser() {
    Account account = registerStudent(mvc);
    Session session = login(mvc, account.email(), PASSWORD);

    MvcTestResult result = me(mvc, session.accessToken());

    assertThat(result).hasStatusOk();
    assertThat(result)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id": "%s", "email": "%s", "role": "STUDENT"}
            """
                .formatted(account.id(), account.email()));
  }

  @Test
  void wrongPasswordUnknownEmailAndDisabledAccountGiveTheSameResponse() {
    Account account = registerStudent(mvc);
    Account disabled = registerStudent(mvc);
    jdbc.sql("UPDATE app_user SET enabled = false WHERE id = :id")
        .param("id", disabled.id())
        .update();

    MvcTestResult wrongPassword = loginRequest(mvc, account.email(), "not the password");
    MvcTestResult unknownEmail = loginRequest(mvc, uniqueEmail(), PASSWORD);
    MvcTestResult disabledAccount = loginRequest(mvc, disabled.email(), PASSWORD);

    for (MvcTestResult result :
        new MvcTestResult[] {wrongPassword, unknownEmail, disabledAccount}) {
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BAD_CREDENTIALS");
      assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
    assertThat(body(unknownEmail)).isEqualTo(body(wrongPassword));
    assertThat(body(disabledAccount)).isEqualTo(body(wrongPassword));
  }

  @Test
  void emailCaseAndSpacesDoNotMatterAtSignIn() {
    Account account = registerStudent(mvc);

    assertThat(loginRequest(mvc, "  " + account.email().toUpperCase() + " ", PASSWORD))
        .hasStatusOk();
  }

  @Test
  void signInIsRecordedWithItsTime() {
    Account account = registerStudent(mvc);

    login(mvc, account.email(), PASSWORD);

    OffsetDateTime lastLogin =
        jdbc.sql("SELECT last_login_at FROM app_user WHERE id = :id")
            .param("id", account.id())
            .query(OffsetDateTime.class)
            .single();
    assertThat(lastLogin.toInstant()).isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
    assertThat(AuditRows.forResource(jdbc, account.id().toString()))
        .extracting(row -> row.get("action"))
        .containsExactly("USER_REGISTERED", "LOGIN_SUCCESS");
  }

  @Test
  void aFailedSignInIsRecordedWithoutThePassword() {
    String email = uniqueEmail();

    loginRequest(mvc, email, "the wrong password");

    assertThat(AuditRows.forActorEmail(jdbc, email))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row)
                  .containsEntry("action", "LOGIN_FAILURE")
                  .containsEntry("outcome", "DENIED");
              assertThat((String) row.get("details"))
                  .contains("credentials")
                  .doesNotContain("the wrong password");
            });
  }

  /** Reads the cookie the way a browser would see it. */
  static final class SetCookie {

    private SetCookie() {}

    static String value(MvcTestResult result) {
      return result.getResponse().getCookie("refresh_token").getValue();
    }

    static long maxAge(String setCookie) {
      for (String part : setCookie.split(";")) {
        String attribute = part.strip();
        if (attribute.startsWith("Max-Age=")) {
          return Long.parseLong(attribute.substring("Max-Age=".length()));
        }
      }
      throw new AssertionError("No Max-Age in " + setCookie);
    }
  }
}

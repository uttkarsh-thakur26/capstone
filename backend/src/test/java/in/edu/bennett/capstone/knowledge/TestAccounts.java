package in.edu.bennett.capstone.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Registers accounts and signs them in through the real endpoints, for integration tests. */
public final class TestAccounts {

  /** Configured in application-test.yaml as the one allowed origin. */
  public static final String ORIGIN = "http://localhost:3000";

  public static final String PASSWORD = "correct horse battery";
  public static final String ADMIN_EMAIL = "admin@test.invalid";
  public static final String ADMIN_PASSWORD = "test-only-admin-password";

  private TestAccounts() {}

  public record Account(UUID id, String email) {}

  /** What sign-in returned: the access token and the refresh token from the cookie. */
  public record Session(String accessToken, String refreshToken) {

    public String bearer() {
      return "Bearer " + accessToken;
    }

    public Cookie refreshCookie() {
      return new Cookie("refresh_token", refreshToken);
    }
  }

  public static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.org";
  }

  public static Account registerStudent(MockMvcTester mvc) {
    String email = uniqueEmail();
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "displayName": "Test User", "password": "%s"}
                """
                    .formatted(email, PASSWORD))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return new Account(UUID.fromString(JsonPath.read(body(result), "$.id")), email);
  }

  /**
   * Registers an account through the endpoint and gives it a role directly in the database, for
   * tests whose subject is not the role change itself.
   */
  public static Account registerAs(MockMvcTester mvc, JdbcClient jdbc, String role) {
    Account account = registerStudent(mvc);
    jdbc.sql("UPDATE app_user SET role = :role WHERE id = :id")
        .param("role", role)
        .param("id", account.id())
        .update();
    return account;
  }

  public static Session login(MockMvcTester mvc, Account account) {
    return login(mvc, account.email(), PASSWORD);
  }

  public static MvcTestResult loginRequest(MockMvcTester mvc, String email, String password) {
    return mvc.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email": "%s", "password": "%s"}
            """
                .formatted(email, password))
        .exchange();
  }

  public static Session login(MockMvcTester mvc, String email, String password) {
    MvcTestResult result = loginRequest(mvc, email, password);
    assertThat(result).hasStatusOk();
    return session(result);
  }

  public static Session loginAsAdmin(MockMvcTester mvc) {
    return login(mvc, ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  /** Reads the access token from the body and the refresh token from {@code Set-Cookie}. */
  public static Session session(MvcTestResult result) {
    Cookie cookie = result.getResponse().getCookie("refresh_token");
    assertThat(cookie).isNotNull();
    return new Session(JsonPath.read(body(result), "$.accessToken"), cookie.getValue());
  }

  public static MvcTestResult refresh(MockMvcTester mvc, String refreshToken) {
    return mvc.post()
        .uri("/api/v1/auth/refresh")
        .header(HttpHeaders.ORIGIN, ORIGIN)
        .cookie(new Cookie("refresh_token", refreshToken))
        .exchange();
  }

  public static MvcTestResult me(MockMvcTester mvc, String accessToken) {
    return mvc.get()
        .uri("/api/v1/auth/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .exchange();
  }

  public static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new UncheckedIOException(e);
    }
  }
}

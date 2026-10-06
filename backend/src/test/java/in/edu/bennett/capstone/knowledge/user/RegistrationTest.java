package in.edu.bennett.capstone.knowledge.user;

import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Self-registration: AUTH-1, AUTH-4, AUTH-10, and the 19.2 test that a role is ignored. */
@IntegrationTest
class RegistrationTest {

  private static final String REGISTER = "/api/v1/auth/register";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void registrationIgnoresARoleInTheRequest() {
    String email = uniqueEmail();

    MvcTestResult result =
        register(
            """
            {"email": "%s", "displayName": "Role Tester",
             "password": "correct horse battery", "role": "ADMIN"}
            """
                .formatted(email));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("STUDENT");
    assertThat(column(email, "role")).isEqualTo("STUDENT");
  }

  @Test
  void responseCarriesNoPasswordData() {
    MvcTestResult result = register(body(uniqueEmail(), "correct horse battery"));

    assertThat(result)
        .bodyJson()
        .extractingPath("$")
        .asMap()
        .containsOnlyKeys("id", "email", "displayName", "role");
  }

  @Test
  void storesAnArgon2HashNotThePassword() {
    String email = uniqueEmail();

    register(body(email, "correct horse battery"));

    assertThat(column(email, "password_hash"))
        .startsWith("{argon2@SpringSecurity_v5_8}$argon2id$")
        .doesNotContain("correct horse battery");
  }

  @Test
  void emailIsTrimmedAndLowerCased() {
    String local = "Mixed.Case-" + UUID.randomUUID();

    MvcTestResult result = register(body("  " + local + "@Example.ORG ", "correct horse battery"));

    String stored = (local + "@example.org").toLowerCase();
    assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(stored);
    assertThat(column(stored, "email")).isEqualTo(stored);
  }

  @Test
  void aTakenEmailIsRefusedWhateverItsCase() {
    String email = uniqueEmail();
    register(body(email, "correct horse battery"));

    MvcTestResult result = register(body(email.toUpperCase(), "another good password"));

    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EMAIL_TAKEN");
  }

  static Stream<Arguments> passwords() {
    return Stream.of(
        Arguments.of("nine char", HttpStatus.BAD_REQUEST),
        Arguments.of("ten chars!", HttpStatus.CREATED),
        Arguments.of("a".repeat(128), HttpStatus.CREATED),
        Arguments.of("a".repeat(129), HttpStatus.BAD_REQUEST),
        // Nine emoji are 18 Java chars; AUTH-4 counts characters, so this is too short.
        Arguments.of("😀".repeat(9), HttpStatus.BAD_REQUEST),
        Arguments.of("😀".repeat(10), HttpStatus.CREATED),
        // 128 Devanagari letters are 384 bytes in UTF-8, far beyond bcrypt's 72 (ADR-022).
        Arguments.of("क".repeat(128), HttpStatus.CREATED));
  }

  @ParameterizedTest
  @MethodSource("passwords")
  void passwordLengthIsTenTo128Characters(String password, HttpStatus expected) {
    MvcTestResult result = register(body(uniqueEmail(), password));

    assertThat(result).hasStatus(expected);
    if (expected == HttpStatus.BAD_REQUEST) {
      assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
    }
  }

  @Test
  void invalidInputIsRefusedWithTheFieldNames() {
    MvcTestResult result =
        register(
            """
            {"email": "not-an-email", "displayName": "  ", "password": "correct horse battery"}
            """);

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactlyInAnyOrder("email", "displayName");
  }

  private MvcTestResult register(String json) {
    return mvc.post()
        .uri(REGISTER)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  private static String body(String email, String password) {
    return """
        {"email": "%s", "displayName": "Test User", "password": "%s"}
        """
        .formatted(email, password);
  }

  private String column(String email, String column) {
    return jdbc.sql("SELECT " + column + " FROM app_user WHERE email = :email")
        .param("email", email)
        .query(String.class)
        .single();
  }

  static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.org";
  }
}

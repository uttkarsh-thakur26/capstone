package in.edu.bennett.capstone.knowledge.common;

import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerAs;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.get;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.send;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.upload;
import static in.edu.bennett.capstone.knowledge.document.TestDocuments.uploaded;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.TestPdfs;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Text with control characters is refused at the boundary with a 400 (15.1). PostgreSQL cannot
 * store or compare a NUL character in text, so without the check such a request reaches the
 * database and fails as a 500. The JSON bodies below carry the escape {@code \u0000}, which the
 * JSON parser turns into the character itself.
 */
@IntegrationTest
class ControlCharacterTest {

  private static final String NUL = "\\u0000";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session faculty;

  @BeforeEach
  void signIn() {
    faculty = login(mvc, registerAs(mvc, jdbc, "FACULTY"));
  }

  @Test
  void documentTextFieldsRefuseControlCharacters() {
    UUID id = uploaded(mvc, faculty, "Clean", "[]");

    assertRefused(
        upload(mvc, faculty, TestPdfs.unique(), "a.pdf", "{\"title\": \"Bad" + NUL + "title\"}"),
        "title");
    assertRefused(
        upload(
            mvc,
            faculty,
            TestPdfs.unique(),
            "a.pdf",
            "{\"title\": \"Fine\", \"description\": \"x" + NUL + "\"}"),
        "description");
    assertRefused(
        send(
            mvc,
            faculty,
            HttpMethod.PATCH,
            "/api/v1/documents/" + id,
            "{\"title\": \"Fine\", \"category\": \"" + NUL + "\"}"),
        "category");
  }

  @Test
  void aDescriptionMayStillHaveLineBreaksAndTabs() {
    MvcTestResult result =
        upload(
            mvc,
            faculty,
            TestPdfs.unique(),
            "a.pdf",
            "{\"title\": \"Fine\", \"description\": \"line one\\nline two\\r\\n\\tindented\"}");

    assertThat(result).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void searchParametersRefuseControlCharacters() {
    assertRefused(get(mvc, faculty, "/api/v1/documents?query=%00"), "query");
    assertRefused(get(mvc, faculty, "/api/v1/documents?category=%00"), "category");
    assertRefused(get(mvc, faculty, "/api/v1/users/search?query=abc%00"), "query");
  }

  /** The same rule for the account inputs of Phase 1 that reach SQL. */
  @Test
  void accountInputsRefuseControlCharacters() {
    assertRefused(
        json(
            "/api/v1/auth/register",
            """
            {"email": "%s", "displayName": "Bad%sname", "password": "correct horse battery"}
            """
                .formatted(TestAccounts.uniqueEmail(), NUL)),
        "displayName");
    assertRefused(
        json(
            "/api/v1/auth/register",
            """
            {"email": "nul%s@example.org", "displayName": "Name",
             "password": "correct horse battery"}
            """
                .formatted(NUL)),
        "email");
    assertRefused(
        json(
            "/api/v1/auth/login",
            """
            {"email": "nul%s@example.org", "password": "correct horse battery"}
            """
                .formatted(NUL)),
        "email");
    assertRefused(
        get(mvc, TestAccounts.loginAsAdmin(mvc), "/api/v1/admin/users?query=%00"), "query");
  }

  private MvcTestResult json(String uri, String body) {
    return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
  }

  private static void assertRefused(MvcTestResult result, String field) {
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
  }
}

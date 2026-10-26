package in.edu.bennett.capstone.knowledge.user;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginAsAdmin;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerAs;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** DOC-8: looking up people to share a document with. */
@IntegrationTest
class UserSearchTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void facultyFindEnabledUsersByNameOrEmailAndSeeOnlyIdNameAndEmail() {
    String marker = UUID.randomUUID().toString().substring(0, 8);
    Account named = registerNamed("Asha " + marker);
    Account disabled = registerNamed("Ravi " + marker);
    jdbc.sql("UPDATE app_user SET enabled = false WHERE id = :id")
        .param("id", disabled.id())
        .update();
    Session faculty = login(mvc, registerAs(mvc, jdbc, "FACULTY"));

    MvcTestResult byName = search(faculty, marker.toUpperCase());
    MvcTestResult byEmail = search(faculty, named.email().substring(0, 12));

    assertThat(byName).hasStatusOk();
    List<Map<String, Object>> items = JsonPath.read(body(byName), "$.items");
    assertThat(items)
        .singleElement()
        .isEqualTo(
            Map.of(
                "id", named.id().toString(),
                "displayName", "Asha " + marker,
                "email", named.email()));
    List<String> emails = JsonPath.read(body(byEmail), "$.items[*].email");
    assertThat(emails).contains(named.email());
  }

  @Test
  void adminsMaySearchToo() {
    assertThat(search(loginAsAdmin(mvc), "test")).hasStatusOk();
  }

  @Test
  void aStudentCannotSearchAndTheRefusalIsAudited() {
    Account student = registerStudent(mvc);

    MvcTestResult result = search(login(mvc, student), "test");

    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(AuditRows.forActorEmail(jdbc, student.email()))
        .extracting(row -> row.get("action"))
        .contains("ACCESS_DENIED");
  }

  @Test
  void aQueryNeedsThreeCharactersAndMatchesWildcardsLiterally() {
    Session faculty = login(mvc, registerAs(mvc, jdbc, "FACULTY"));

    for (String tooShort : new String[] {"ab", "  ab  ", ""}) {
      MvcTestResult result = search(faculty, tooShort);
      assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }
    assertThat(search(faculty, "ab"))
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("QUERY_TOO_SHORT");
    assertThat(search(faculty, "%%%")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
  }

  private Account registerNamed(String displayName) {
    String email = "search-" + UUID.randomUUID() + "@example.org";
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "displayName": "%s", "password": "correct horse battery"}
                """
                    .formatted(email, displayName))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return new Account(UUID.fromString(JsonPath.read(body(result), "$.id")), email);
  }

  /** Spaces as %20: MockMvc does not decode + in a query string, unlike a servlet container. */
  private MvcTestResult search(Session session, String query) {
    return mvc.get()
        .uri(
            URI.create(
                "/api/v1/users/search?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")))
        .header(HttpHeaders.AUTHORIZATION, session.bearer())
        .exchange();
  }
}

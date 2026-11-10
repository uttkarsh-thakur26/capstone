package in.edu.bennett.capstone.knowledge.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.document.TestDocuments;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Reading the audit log (AUD-3, 15.2 {@code GET /admin/audit}): filters by actor, action, outcome
 * and time, newest first, paged, with the details as recorded.
 */
@IntegrationTest
class AuditReadTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  private Session admin;
  private Account student;

  @BeforeEach
  void setUp() {
    admin = TestAccounts.loginAsAdmin(mvc);
    student = TestAccounts.registerStudent(mvc);
    TestAccounts.login(mvc, student);
    TestAccounts.loginRequest(mvc, student.email(), "not the password at all");
  }

  @Test
  void anActorsEntriesComeNewestFirstWithTheirDetails() {
    List<Map<String, Object>> items = items("/api/v1/admin/audit?actor=" + encode(student.email()));

    assertThat(items)
        .extracting(item -> item.get("action"))
        .containsExactly("LOGIN_FAILURE", "LOGIN_SUCCESS", "USER_REGISTERED");
    Map<String, Object> failure = items.getFirst();
    assertThat(failure.get("outcome")).isEqualTo("DENIED");
    assertThat(failure.get("actorEmail")).isEqualTo(student.email());
    assertThat(failure.get("ipAddress")).isEqualTo("127.0.0.1");
    assertThat(failure.get("details")).isEqualTo(Map.of("reason", "credentials"));
    assertThat(items.get(1).get("actorUserId")).isEqualTo(student.id().toString());
  }

  @Test
  void actionOutcomeAndTimeNarrowTheList() {
    String actor = "actor=" + encode(student.email().toUpperCase());

    assertThat(items("/api/v1/admin/audit?" + actor + "&action=LOGIN_SUCCESS"))
        .extracting(item -> item.get("action"))
        .containsExactly("LOGIN_SUCCESS");
    assertThat(items("/api/v1/admin/audit?" + actor + "&outcome=DENIED"))
        .extracting(item -> item.get("action"))
        .containsExactly("LOGIN_FAILURE");
    Instant later = Instant.now().plus(1, ChronoUnit.HOURS);
    assertThat(items("/api/v1/admin/audit?" + actor + "&from=" + later)).isEmpty();
    assertThat(items("/api/v1/admin/audit?" + actor + "&to=" + later)).hasSize(3);
  }

  @Test
  void wildcardsInTheActorFilterMatchThemselves() {
    assertThat(items("/api/v1/admin/audit?actor=%25")).isEmpty();
  }

  @Test
  void pagesHoldTheRequestedSize() {
    MvcTestResult result =
        TestDocuments.get(
            mvc, admin, "/api/v1/admin/audit?size=2&page=0&actor=" + encode(student.email()));

    assertThat(result).hasStatusOk();
    String body = TestAccounts.body(result);
    assertThat((List<?>) JsonPath.read(body, "$.items")).hasSize(2);
    assertThat((Integer) JsonPath.read(body, "$.totalItems")).isEqualTo(3);
    assertThat((Integer) JsonPath.read(body, "$.totalPages")).isEqualTo(2);
  }

  @Test
  void aPeriodThatEndsBeforeItStartsIsRefused() {
    Instant now = Instant.now();
    MvcTestResult result =
        TestDocuments.get(
            mvc, admin, "/api/v1/admin/audit?from=" + now + "&to=" + now.minusSeconds(60));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_RANGE");
  }

  private List<Map<String, Object>> items(String uri) {
    MvcTestResult result = TestDocuments.get(mvc, admin, uri);
    assertThat(result).hasStatusOk();
    return JsonPath.read(TestAccounts.body(result), "$.items");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

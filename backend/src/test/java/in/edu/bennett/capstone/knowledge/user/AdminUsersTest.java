package in.edu.bennett.capstone.knowledge.user;

import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginAsAdmin;
import static in.edu.bennett.capstone.knowledge.TestAccounts.me;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * User administration (14.1, AUTH-2, AUTH-8): the Phase 1 exit criterion that the bootstrap admin
 * promotes a registered user to FACULTY with effect on that user's next request, and the 19.2 tests
 * that a non-admin gets 403 and that a role change applies on the next request.
 */
@IntegrationTest
class AdminUsersTest {

  private static final String ADMIN_USERS = "/api/v1/admin/users";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired UserService users;

  private Session admin;

  @BeforeEach
  void signInAsTheBootstrapAdmin() {
    // The account created at startup from app.bootstrap-admin in application-test.yaml (AUTH-3).
    admin = loginAsAdmin(mvc);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void theBootstrapAdminPromotesARegisteredUserToFacultyEffectiveOnTheirNextRequest() {
    Account student = registerStudent(mvc);
    Session studentSession = login(mvc, student.email(), PASSWORD);
    assertThat(me(mvc, studentSession.accessToken()))
        .bodyJson()
        .extractingPath("$.role")
        .isEqualTo("STUDENT");

    MvcTestResult result = patch(admin, student.id(), "{\"role\": \"FACULTY\"}");

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("FACULTY");
    // The student's token was issued before the change; the next request already sees FACULTY.
    assertThat(me(mvc, studentSession.accessToken()))
        .bodyJson()
        .extractingPath("$.role")
        .isEqualTo("FACULTY");
    String adminId = JsonPath.read(body(me(mvc, admin.accessToken())), "$.id");
    assertThat(AuditRows.forResource(jdbc, student.id().toString()))
        .filteredOn(row -> "ROLE_CHANGED".equals(row.get("action")))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row)
                  .containsEntry("outcome", "SUCCESS")
                  .containsEntry("actor_user_id", adminId)
                  .containsEntry("actor_email", TestAccounts.ADMIN_EMAIL);
              assertThat((String) row.get("details"))
                  .contains("\"from\": \"STUDENT\"")
                  .contains("\"to\": \"FACULTY\"");
            });
  }

  @Test
  void aRoleChangeAppliesToAccessOnTheNextRequest() {
    Account student = registerStudent(mvc);
    Session session = login(mvc, student.email(), PASSWORD);
    assertThat(get(session, ADMIN_USERS)).hasStatus(HttpStatus.FORBIDDEN);

    patch(admin, student.id(), "{\"role\": \"ADMIN\"}");
    assertThat(get(session, ADMIN_USERS)).hasStatusOk();

    patch(admin, student.id(), "{\"role\": \"STUDENT\"}");
    assertThat(get(session, ADMIN_USERS)).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void disablingAUserTakesEffectOnTheirNextRequestAndIsAudited() {
    Account student = registerStudent(mvc);
    Session session = login(mvc, student.email(), PASSWORD);

    MvcTestResult disabled = patch(admin, student.id(), "{\"enabled\": false}");

    assertThat(disabled).hasStatusOk();
    assertThat(disabled).bodyJson().extractingPath("$.enabled").isEqualTo(false);
    assertThat(me(mvc, session.accessToken())).hasStatus(HttpStatus.UNAUTHORIZED);

    patch(admin, student.id(), "{\"enabled\": true}");
    assertThat(me(mvc, session.accessToken())).hasStatusOk();
    assertThat(AuditRows.forResource(jdbc, student.id().toString()))
        .extracting(row -> row.get("action"))
        .containsSubsequence("USER_DISABLED", "USER_ENABLED");
  }

  @Test
  void studentsAndFacultyGet403OnEveryAdminEndpoint() {
    Account target = registerStudent(mvc);
    Account student = registerStudent(mvc);
    Account faculty = registerStudent(mvc);
    jdbc.sql("UPDATE app_user SET role = 'FACULTY' WHERE id = :id")
        .param("id", faculty.id())
        .update();

    for (Account caller : List.of(student, faculty)) {
      Session session = login(mvc, caller.email(), PASSWORD);
      assertThat(get(session, ADMIN_USERS)).hasStatus(HttpStatus.FORBIDDEN);
      MvcTestResult promote = patch(session, target.id(), "{\"role\": \"ADMIN\"}");
      assertThat(promote).hasStatus(HttpStatus.FORBIDDEN);
      assertThat(promote).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }
    assertThat(
            jdbc.sql("SELECT role FROM app_user WHERE id = :id")
                .param("id", target.id())
                .query(String.class)
                .single())
        .isEqualTo("STUDENT");
  }

  @Test
  void methodSecurityGuardsTheServiceOnItsOwn() {
    UpdateUserRequest promote = new UpdateUserRequest(Role.ADMIN, null);
    UUID someone = UUID.randomUUID();

    assertThatExceptionOfType(AuthenticationCredentialsNotFoundException.class)
        .isThrownBy(() -> users.listUsers(new UserQuery(null, null, null), 0, 20, null));

    signInAs("ROLE_STUDENT");
    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(() -> users.listUsers(new UserQuery(null, null, null), 0, 20, null));
    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(() -> users.updateUser(someone, someone, promote));

    signInAs("ROLE_ADMIN");
    assertThat(users.listUsers(new UserQuery(null, null, null), 0, 1, null).items()).hasSize(1);
  }

  @Test
  void theListIsSearchedFilteredSortedAndPaged() {
    String marker = "Pager " + UUID.randomUUID();
    List<String> emails =
        List.of(register(marker + " C"), register(marker + " A"), register(marker + " B"));

    MvcTestResult first =
        get(admin, ADMIN_USERS + "?query=" + marker.toUpperCase() + "&size=2&sort=email,asc");

    assertThat(first).hasStatusOk();
    assertThat(first)
        .bodyJson()
        .isLenientlyEqualTo("{\"page\": 0, \"size\": 2, \"totalItems\": 3, \"totalPages\": 2}");
    List<String> sorted = emails.stream().sorted().toList();
    assertThat(first)
        .bodyJson()
        .extractingPath("$.items[*].email")
        .asArray()
        .containsExactly(sorted.get(0), sorted.get(1));
    assertThat(first)
        .bodyJson()
        .extractingPath("$.items[0]")
        .asMap()
        .containsOnlyKeys(
            "id", "email", "displayName", "role", "enabled", "createdAt", "lastLoginAt");

    assertThat(get(admin, ADMIN_USERS + "?query=" + marker + "&size=2&page=1"))
        .bodyJson()
        .extractingPath("$.items[*].email")
        .asArray()
        .containsExactly(sorted.get(2));
    assertThat(get(admin, ADMIN_USERS + "?query=" + marker + "&role=FACULTY"))
        .bodyJson()
        .extractingPath("$.totalItems")
        .isEqualTo(0);
    assertThat(get(admin, ADMIN_USERS + "?query=" + marker + "&enabled=true&role=STUDENT"))
        .bodyJson()
        .extractingPath("$.totalItems")
        .isEqualTo(3);
  }

  @Test
  void likeWildcardsInTheSearchAreMatchedLiterally() {
    for (String wildcard : List.of("%25", "_")) {
      assertThat(get(admin, ADMIN_USERS + "?query=" + wildcard))
          .bodyJson()
          .extractingPath("$.totalItems")
          .isEqualTo(0);
    }
  }

  @Test
  void invalidPagingAndSortingAreRefused() {
    assertCode(get(admin, ADMIN_USERS + "?size=101"), "VALIDATION_FAILED");
    assertCode(get(admin, ADMIN_USERS + "?page=-1"), "VALIDATION_FAILED");
    assertCode(get(admin, ADMIN_USERS + "?sort=password_hash"), "INVALID_SORT");
    assertCode(get(admin, ADMIN_USERS + "?sort=email,sideways"), "INVALID_SORT");
  }

  @Test
  void invalidChangesAreRefused() {
    Account student = registerStudent(mvc);

    assertThat(patch(admin, UUID.randomUUID(), "{\"role\": \"FACULTY\"}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertCode(patch(admin, student.id(), "{}"), "VALIDATION_FAILED");
    assertCode(patch(admin, student.id(), "{\"role\": \"SUPERUSER\"}"), "BAD_REQUEST");
  }

  private String register(String displayName) {
    String email = TestAccounts.uniqueEmail();
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "displayName": "%s", "password": "%s"}
                    """
                        .formatted(email, displayName, PASSWORD)))
        .hasStatus(HttpStatus.CREATED);
    return email;
  }

  private MvcTestResult get(Session session, String uri) {
    return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
  }

  private MvcTestResult patch(Session session, UUID userId, String json) {
    return mvc.patch()
        .uri(ADMIN_USERS + "/" + userId)
        .header(HttpHeaders.AUTHORIZATION, session.bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  private static void assertCode(MvcTestResult result, String code) {
    assertThat(result).hasStatus4xxClientError();
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
  }

  private static void signInAs(String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "someone", null, List.of(new SimpleGrantedAuthority(authority))));
  }
}

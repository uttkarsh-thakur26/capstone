package in.edu.bennett.capstone.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.analytics.AnalyticsService;
import in.edu.bennett.capstone.knowledge.audit.AuditLogReader;
import in.edu.bennett.capstone.knowledge.audit.AuditQuery;
import in.edu.bennett.capstone.knowledge.document.DocumentService;
import in.edu.bennett.capstone.knowledge.user.Role;
import in.edu.bennett.capstone.knowledge.user.UpdateUserRequest;
import in.edu.bennett.capstone.knowledge.user.UserService;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Phase 9 exit criterion: a non-admin gets 403 from every admin endpoint (AUTH-8, 19.2). The
 * endpoints are not listed by hand: they are read from Spring's own mappings, so an admin endpoint
 * added later is covered without touching this test. Method security is checked as well, by calling
 * each admin service method directly as a faculty member.
 */
@IntegrationTest
class AdminEndpointsForbiddenTest {

  /** Every admin endpoint as built in Phase 9, so an empty discovery cannot pass silently. */
  private static final Set<String> KNOWN =
      Set.of(
          "GET /api/v1/admin/users",
          "PATCH /api/v1/admin/users/{id}",
          "POST /api/v1/admin/users/{id}/password",
          "GET /api/v1/admin/documents",
          "POST /api/v1/admin/reindex",
          "GET /api/v1/admin/audit",
          "GET /api/v1/admin/analytics");

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Autowired UserService users;

  @Autowired DocumentService documents;

  @Autowired AuditLogReader audit;

  @Autowired AnalyticsService analytics;

  @AfterEach
  void clearTheSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void studentsAndFacultyGet403FromEveryAdminEndpoint() {
    List<String> endpoints = adminEndpoints();
    assertThat(endpoints).containsAll(KNOWN);
    Account studentAccount = TestAccounts.registerStudent(mvc);
    Session student = TestAccounts.login(mvc, studentAccount);
    Session faculty = TestAccounts.login(mvc, TestAccounts.registerAs(mvc, jdbc, "FACULTY"));

    for (String endpoint : endpoints) {
      for (Session session : List.of(student, faculty)) {
        MvcTestResult result = call(endpoint, session);
        assertThat(result).as(endpoint).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).as(endpoint).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
      }
      assertThat(call(endpoint, null)).as(endpoint).hasStatus(HttpStatus.UNAUTHORIZED);
    }
    // Each refusal of the student is an ACCESS_DENIED entry naming the endpoint (14.2).
    long denied =
        jdbc.sql(
                "SELECT count(*) FROM audit_log WHERE action = 'ACCESS_DENIED'"
                    + " AND actor_user_id = :id AND resource_type = 'ENDPOINT'")
            .param("id", studentAccount.id())
            .query(Long.class)
            .single();
    assertThat(denied).isEqualTo(endpoints.size());
  }

  /** AUTH-8: the services refuse too, should a URL rule ever be lost. */
  @Test
  void everyAdminServiceMethodRefusesAFacultyMember() {
    Account account = TestAccounts.registerAs(mvc, jdbc, "FACULTY");
    CurrentUser member = new CurrentUser(account.id(), account.email(), "Member", Role.FACULTY);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(member, null, member.authorities()));

    List<Executable> calls =
        List.of(
            () -> users.listUsers(null, 0, 10, null),
            () ->
                users.updateUser(member.id(), UUID.randomUUID(), new UpdateUserRequest(null, true)),
            () -> users.resetPassword(member.id(), UUID.randomUUID(), "a long new password"),
            () -> documents.listForAdmin(member, null, 0, 10, null),
            () -> documents.reindexAll(member),
            () -> audit.list(new AuditQuery(null, null, null, null, null), 0, 10),
            () -> analytics.analytics(30, "UTC"));
    for (Executable call : calls) {
      assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(call::execute);
    }
  }

  /** "METHOD path" for every mapping under /api/v1/admin. */
  private List<String> adminEndpoints() {
    List<String> endpoints = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping :
        mappings.getHandlerMethods().entrySet()) {
      for (String path : mapping.getKey().getPatternValues()) {
        if (!path.startsWith("/api/v1/admin")) {
          continue;
        }
        Set<RequestMethod> methods = mapping.getKey().getMethodsCondition().getMethods();
        assertThat(methods).as("%s names its HTTP method", path).isNotEmpty();
        methods.forEach(method -> endpoints.add(method.name() + " " + path));
      }
    }
    return endpoints;
  }

  private MvcTestResult call(String endpoint, Session session) {
    String[] parts = endpoint.split(" ", 2);
    String path = parts[1].replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
    MockMvcTester.MockMvcRequestBuilder request =
        mvc.method(HttpMethod.valueOf(parts[0])).uri(URI.create(path));
    if (session != null) {
      request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
    if (!parts[0].equals("GET") && !parts[0].equals("DELETE")) {
      request.contentType(MediaType.APPLICATION_JSON).content("{}");
    }
    return request.exchange();
  }
}

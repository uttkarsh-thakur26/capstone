package in.edu.bennett.capstone.knowledge.security;

import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** ADR-024: ACCESS_DENIED is recorded for a 403, and a 401 is not recorded. */
@IntegrationTest
class AccessDeniedAuditTest {

  private static final String ADMIN_USERS = "/api/v1/admin/users";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Test
  void aStudentRefusedByARoleRuleIsRecorded() {
    Account student = registerStudent(mvc);
    String token = login(mvc, student.email(), PASSWORD).accessToken();

    assertThat(
            mvc.get()
                .uri(ADMIN_USERS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange())
        .hasStatus(HttpStatus.FORBIDDEN);

    assertThat(AuditRows.forActorEmail(jdbc, student.email()))
        .filteredOn(row -> "ACCESS_DENIED".equals(row.get("action")))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row)
                    .containsEntry("outcome", "DENIED")
                    .containsEntry("actor_user_id", student.id().toString())
                    .containsEntry("resource_type", "ENDPOINT")
                    .containsEntry("resource_id", "GET " + ADMIN_USERS));
  }

  @Test
  void anAnonymousRequestIsNotRecorded() {
    long before = accessDeniedCount();

    assertThat(mvc.get().uri(ADMIN_USERS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

    assertThat(accessDeniedCount()).isEqualTo(before);
  }

  private long accessDeniedCount() {
    return jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'ACCESS_DENIED'")
        .query(Long.class)
        .single();
  }
}

package in.edu.bennett.capstone.knowledge.user;

import static in.edu.bennett.capstone.knowledge.TestAccounts.body;
import static in.edu.bennett.capstone.knowledge.TestAccounts.loginAsAdmin;
import static in.edu.bennett.capstone.knowledge.TestAccounts.me;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import in.edu.bennett.capstone.knowledge.TestAccounts.Session;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 19.2: the last admin cannot be demoted or disabled (AUTH-2), also when two admins demote each
 * other at the same moment. The extra property gives this class a Spring context, and so a
 * database, of its own, in which the bootstrap admin is the only admin at the start.
 */
@IntegrationTest
@TestPropertySource(properties = "test.database=last-admin")
class LastAdminTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired UserService users;

  @Autowired TransactionTemplate transactions;

  private Session admin;
  private UUID adminId;

  @BeforeEach
  void signInAsTheOnlyAdmin() {
    admin = loginAsAdmin(mvc);
    adminId = UUID.fromString(JsonPath.read(body(me(mvc, admin.accessToken())), "$.id"));
    assertThat(enabledAdmins()).containsExactly(adminId);
  }

  @AfterEach
  void restoreTheOnlyAdmin() {
    jdbc.sql("UPDATE app_user SET role = 'STUDENT' WHERE role = 'ADMIN' AND id <> :id")
        .param("id", adminId)
        .update();
    jdbc.sql("UPDATE app_user SET role = 'ADMIN', enabled = true WHERE id = :id")
        .param("id", adminId)
        .update();
  }

  @Test
  void theOnlyAdminCanBeNeitherDemotedNorDisabled() {
    // Other tests of this class share the database, so only the entries added here are checked.
    int earlierEntries = AuditRows.forResource(jdbc, adminId.toString()).size();
    for (String change :
        List.of(
            "{\"role\": \"FACULTY\"}",
            "{\"enabled\": false}",
            "{\"role\": \"STUDENT\", \"enabled\": false}")) {
      MvcTestResult result = patch(adminId, change);

      assertThat(result).hasStatus(HttpStatus.CONFLICT);
      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN");
    }
    assertThat(enabledAdmins()).containsExactly(adminId);
    List<Map<String, Object>> entries = AuditRows.forResource(jdbc, adminId.toString());
    assertThat(entries.subList(earlierEntries, entries.size()))
        .allSatisfy(row -> assertThat(row).containsEntry("outcome", "FAILURE"))
        .extracting(row -> row.get("action"))
        .containsExactly("ROLE_CHANGED", "USER_DISABLED", "ROLE_CHANGED");
  }

  @Test
  void withASecondAdminOneOfThemMayBeDemoted() {
    Account second = registerStudent(mvc);
    assertThat(patch(second.id(), "{\"role\": \"ADMIN\"}")).hasStatusOk();

    assertThat(patch(second.id(), "{\"role\": \"FACULTY\"}")).hasStatusOk();

    assertThat(enabledAdmins()).containsExactly(adminId);
    assertThat(patch(adminId, "{\"enabled\": false}")).hasStatus(HttpStatus.CONFLICT);
  }

  /**
   * Two admins, each demoting the other at the same moment. One request is simulated by a
   * transaction that locks the admins' rows and demotes the bootstrap admin; while it holds the
   * lock, the service is asked to demote the second admin. The service must wait for the lock, then
   * see that only the second admin is left, and refuse. Without the lock it would read both admins
   * as enabled, and both demotions would succeed, leaving no admin.
   */
  @Test
  void twoAdminsDemotingEachOtherAtOnceLeaveOneAdmin() throws Exception {
    Account second = registerStudent(mvc);
    assertThat(patch(second.id(), "{\"role\": \"ADMIN\"}")).hasStatusOk();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<?> other =
          threads.submit(
              () ->
                  transactions.executeWithoutResult(
                      status -> {
                        jdbc.sql(
                                "SELECT id FROM app_user WHERE role = 'ADMIN' AND enabled"
                                    + " FOR UPDATE")
                            .query()
                            .listOfRows();
                        locked.countDown();
                        awaitQuietly(release);
                        jdbc.sql("UPDATE app_user SET role = 'STUDENT' WHERE id = :id")
                            .param("id", adminId)
                            .update();
                      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

      Future<AdminUserResponse> mine =
          threads.submit(
              () -> {
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                try {
                  return users.updateUser(
                      adminId, second.id(), new UpdateUserRequest(Role.FACULTY, null));
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      // Proves the service reached the database and is blocked on the lock, not merely slow.
      awaitASessionWaitingForALock();
      release.countDown();
      other.get(10, TimeUnit.SECONDS);

      assertThatThrownBy(() -> mine.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.code()).isEqualTo("LAST_ADMIN"));
      assertThat(enabledAdmins()).containsExactly(second.id());
    } finally {
      release.countDown();
      threads.shutdownNow();
    }
  }

  private void awaitASessionWaitingForALock() throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    while (Instant.now().isBefore(deadline)) {
      long waiting =
          jdbc.sql(
                  "SELECT count(*) FROM pg_stat_activity"
                      + " WHERE wait_event_type = 'Lock' AND datname = current_database()")
              .query(Long.class)
              .single();
      if (waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("The service never waited for the admins' row lock");
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private List<UUID> enabledAdmins() {
    return jdbc.sql("SELECT id FROM app_user WHERE role = 'ADMIN' AND enabled ORDER BY id")
        .query(UUID.class)
        .list();
  }

  private MvcTestResult patch(UUID userId, String json) {
    return mvc.patch()
        .uri("/api/v1/admin/users/" + userId)
        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }
}

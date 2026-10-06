package in.edu.bennett.capstone.knowledge.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** AUD-1, AUD-2, AUD-4 and the append-only rule of 7.2 (ADR-024). */
@IntegrationTest
class AuditServiceTest {

  @Autowired AuditService audit;

  @Autowired JdbcClient jdbc;

  @Autowired TransactionTemplate transactions;

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void recordsEveryFieldWithTheTimeAndTheClientAddress() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.7");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    String resourceId = UUID.randomUUID().toString();
    Instant before = Instant.now();

    audit.record(
        AuditEvent.of(AuditAction.ROLE_CHANGED, AuditOutcome.SUCCESS, null, "actor@example.org")
            .withResource("USER", resourceId)
            .withDetails(Map.of("from", "STUDENT", "to", "FACULTY")));

    Map<String, Object> row = AuditRows.forResource(jdbc, resourceId).getFirst();
    assertThat(row)
        .containsEntry("action", "ROLE_CHANGED")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("actor_email", "actor@example.org")
        .containsEntry("resource_type", "USER")
        .containsEntry("ip_address", "203.0.113.7");
    assertThat((String) row.get("details")).contains("\"to\": \"FACULTY\"");
    OffsetDateTime occurredAt =
        jdbc.sql("SELECT occurred_at FROM audit_log WHERE resource_id = :id")
            .param("id", resourceId)
            .query(OffsetDateTime.class)
            .single();
    assertThat(occurredAt.toInstant()).isBetween(before.minusSeconds(1), Instant.now());
  }

  @Test
  void theDatabaseAcceptsEveryAction() {
    String resourceId = UUID.randomUUID().toString();

    for (AuditAction action : AuditAction.values()) {
      audit.record(
          AuditEvent.of(action, AuditOutcome.SUCCESS, null, null).withResource("TEST", resourceId));
    }

    assertThat(AuditRows.forResource(jdbc, resourceId)).hasSize(AuditAction.values().length);
  }

  @Test
  void detailsNeverHoldPasswordsTokensOrSecrets() {
    for (String key : List.of("newPassword", "refreshToken", "clientSecret")) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  AuditEvent.of(AuditAction.LOGOUT, AuditOutcome.SUCCESS, null, null)
                      .withDetails(Map.of(key, "x")));
    }
  }

  @Test
  void entriesCannotBeChangedOrRemoved() {
    String resourceId = UUID.randomUUID().toString();
    audit.record(
        AuditEvent.of(AuditAction.LOGOUT, AuditOutcome.SUCCESS, null, null)
            .withResource("TEST", resourceId));

    for (String sql :
        List.of(
            "UPDATE audit_log SET outcome = 'FAILURE' WHERE resource_id = '" + resourceId + "'",
            "DELETE FROM audit_log WHERE resource_id = '" + resourceId + "'",
            "TRUNCATE audit_log")) {
      assertThatThrownBy(() -> jdbc.sql(sql).update())
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("append-only");
    }
    assertThat(AuditRows.forResource(jdbc, resourceId)).singleElement();
  }

  @Test
  void aSuccessEntryIsRolledBackWithItsAction() {
    String resourceId = UUID.randomUUID().toString();

    transactions.executeWithoutResult(
        status -> {
          audit.record(
              AuditEvent.of(AuditAction.ROLE_CHANGED, AuditOutcome.SUCCESS, null, null)
                  .withResource("TEST", resourceId));
          status.setRollbackOnly();
        });

    assertThat(AuditRows.forResource(jdbc, resourceId)).isEmpty();
  }

  @Test
  void aFailureEntrySurvivesTheRollbackOfItsAction() {
    String resourceId = UUID.randomUUID().toString();

    transactions.executeWithoutResult(
        status -> {
          audit.recordSeparately(
              AuditEvent.of(AuditAction.ROLE_CHANGED, AuditOutcome.FAILURE, null, null)
                  .withResource("TEST", resourceId));
          status.setRollbackOnly();
        });

    assertThat(AuditRows.forResource(jdbc, resourceId)).singleElement();
  }
}

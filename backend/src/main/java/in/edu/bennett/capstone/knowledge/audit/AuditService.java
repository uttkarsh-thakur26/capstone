package in.edu.bennett.capstone.knowledge.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit entries (AUD-4, ADR-024). There is no method that changes or removes one, and the
 * database refuses to as well.
 */
@Service
public class AuditService {

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock clock;

  AuditService(JdbcClient jdbc, JsonMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  /**
   * For a successful action: the entry joins the caller's transaction, so the change and its entry
   * are committed together or not at all.
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public void record(AuditEvent event) {
    insert(event);
  }

  /**
   * For a denied or failed action: the entry is committed in a transaction of its own, so the
   * rollback of the action does not erase it.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSeparately(AuditEvent event) {
    insert(event);
  }

  private void insert(AuditEvent event) {
    jdbc.sql(
            """
            INSERT INTO audit_log (occurred_at, actor_user_id, actor_email, action,
                                   resource_type, resource_id, outcome, ip_address, details)
            VALUES (:occurredAt, :actorUserId, :actorEmail, :action,
                    :resourceType, :resourceId, :outcome, CAST(:ip AS inet), CAST(:details AS jsonb))
            """)
        .param("occurredAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
        .param("actorUserId", event.actorUserId())
        .param("actorEmail", event.actorEmail())
        .param("action", event.action().name())
        .param("resourceType", event.resourceType())
        .param("resourceId", event.resourceId())
        .param("outcome", event.outcome().name())
        .param("ip", clientAddress())
        .param("details", json.writeValueAsString(event.details()))
        .update();
  }

  /**
   * The address of the current request, if there is one. Behind nginx it is the client's address,
   * because the backend reads nginx's {@code X-Forwarded-For} (ADR-024).
   */
  private static String clientAddress() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
      return request.getRequest().getRemoteAddr();
    }
    return null;
  }
}

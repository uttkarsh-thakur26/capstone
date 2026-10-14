package in.edu.bennett.capstone.knowledge;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads audit entries for assertions. Columns come back as text, details as JSON text. */
public final class AuditRows {

  private static final String COLUMNS =
      """
      SELECT action, outcome, actor_user_id::text AS actor_user_id, actor_email, resource_type,
             resource_id, host(ip_address) AS ip_address, details::text AS details
      FROM audit_log
      """;

  private AuditRows() {}

  public static List<Map<String, Object>> forResource(JdbcClient jdbc, String resourceId) {
    return jdbc.sql(COLUMNS + " WHERE resource_id = :id ORDER BY id")
        .param("id", resourceId)
        .query()
        .listOfRows();
  }

  public static List<Map<String, Object>> forActorEmail(JdbcClient jdbc, String email) {
    return jdbc.sql(COLUMNS + " WHERE actor_email = :email ORDER BY id")
        .param("email", email)
        .query()
        .listOfRows();
  }
}

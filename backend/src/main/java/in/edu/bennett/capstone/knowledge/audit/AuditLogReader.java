package in.edu.bennett.capstone.knowledge.audit;

import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.common.LikePatterns;
import in.edu.bennett.capstone.knowledge.common.PageResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reading the audit log (AUD-3): filtered, newest first, paged, for admins only, by URL rule and by
 * method security (AUTH-8). The filters are fixed SQL fragments with bind parameters (SEC-7); no
 * value from the client becomes part of the statement.
 */
@Service
public class AuditLogReader {

  private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AuditLogReader(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public PageResponse<AuditEntryResponse> list(AuditQuery query, int page, int size) {
    if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RANGE",
          "The start of the period must be before its end.");
    }
    List<String> conditions = new ArrayList<>();
    Map<String, Object> params = new HashMap<>();
    if (query.actor() != null && !query.actor().isBlank()) {
      conditions.add("lower(actor_email) LIKE :actor ESCAPE '\\'");
      params.put("actor", LikePatterns.containing(query.actor()));
    }
    if (query.action() != null) {
      conditions.add("action = :action");
      params.put("action", query.action().name());
    }
    if (query.outcome() != null) {
      conditions.add("outcome = :outcome");
      params.put("outcome", query.outcome().name());
    }
    if (query.from() != null) {
      conditions.add("occurred_at >= :from");
      params.put("from", OffsetDateTime.ofInstant(query.from(), ZoneOffset.UTC));
    }
    if (query.to() != null) {
      conditions.add("occurred_at < :to");
      params.put("to", OffsetDateTime.ofInstant(query.to(), ZoneOffset.UTC));
    }
    String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

    long total =
        jdbc.sql("SELECT count(*) FROM audit_log" + where)
            .params(params)
            .query(Long.class)
            .single();
    params.put("limit", size);
    params.put("offset", (long) page * size);
    List<AuditEntryResponse> items =
        jdbc.sql(
                """
                SELECT id, occurred_at, actor_user_id, actor_email, action, resource_type,
                       resource_id, outcome, host(ip_address) AS ip, details::text AS details
                FROM audit_log
                """
                    + where
                    + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset")
            .params(params)
            .query(
                (row, rowNum) ->
                    new AuditEntryResponse(
                        row.getLong("id"),
                        row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        row.getObject("actor_user_id", UUID.class),
                        row.getString("actor_email"),
                        AuditAction.valueOf(row.getString("action")),
                        row.getString("resource_type"),
                        row.getString("resource_id"),
                        AuditOutcome.valueOf(row.getString("outcome")),
                        row.getString("ip"),
                        json.readValue(row.getString("details"), DETAILS)))
            .list();
    int pages = (int) ((total + size - 1) / size);
    return new PageResponse<>(items, page, size, total, pages);
  }
}

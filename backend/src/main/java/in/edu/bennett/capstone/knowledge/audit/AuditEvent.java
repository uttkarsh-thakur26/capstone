package in.edu.bennett.capstone.knowledge.audit;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audit entry (AUD-1). The time and the client address are added by {@link AuditService}.
 *
 * @param actorUserId the acting user, or null when nobody is signed in
 * @param actorEmail a snapshot of the actor's email, kept even if the account changes
 * @param details structured facts about the action. Never passwords, tokens or keys (AUD-2): keys
 *     that name one are refused.
 */
public record AuditEvent(
    AuditAction action,
    AuditOutcome outcome,
    UUID actorUserId,
    String actorEmail,
    String resourceType,
    String resourceId,
    Map<String, Object> details) {

  public AuditEvent {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(outcome, "outcome");
    details = details == null ? Map.of() : Map.copyOf(details);
    for (String key : details.keySet()) {
      String name = key.toLowerCase(Locale.ROOT);
      if (name.contains("password") || name.contains("token") || name.contains("secret")) {
        throw new IllegalArgumentException("Audit details must not hold secrets: " + key);
      }
    }
  }

  public static AuditEvent of(
      AuditAction action, AuditOutcome outcome, UUID actorUserId, String actorEmail) {
    return new AuditEvent(action, outcome, actorUserId, actorEmail, null, null, Map.of());
  }

  public AuditEvent withResource(String type, Object id) {
    return new AuditEvent(
        action, outcome, actorUserId, actorEmail, type, id == null ? null : id.toString(), details);
  }

  public AuditEvent withDetails(Map<String, Object> newDetails) {
    return new AuditEvent(
        action, outcome, actorUserId, actorEmail, resourceType, resourceId, newDetails);
  }
}

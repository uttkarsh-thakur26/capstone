package in.edu.bennett.capstone.knowledge.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One audit entry as an admin reads it (AUD-1). It holds what was recorded and nothing else: never
 * a password, token, key, document text, prompt or answer (AUD-2).
 *
 * @param actorEmail a snapshot taken when the entry was written; for a failed sign-in, the email
 *     that was typed
 * @param details structured facts about the action, such as the names of the fields that changed
 *     (never their values) or the role before and after
 */
public record AuditEntryResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) long id,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant occurredAt,
    UUID actorUserId,
    String actorEmail,
    @Schema(requiredMode = RequiredMode.REQUIRED) AuditAction action,
    String resourceType,
    String resourceId,
    @Schema(requiredMode = RequiredMode.REQUIRED) AuditOutcome outcome,
    String ipAddress,
    @Schema(requiredMode = RequiredMode.REQUIRED) Map<String, Object> details) {}

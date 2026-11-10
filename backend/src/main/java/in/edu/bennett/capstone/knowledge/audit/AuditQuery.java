package in.edu.bennett.capstone.knowledge.audit;

import java.time.Instant;

/**
 * The filters of the audit log (AUD-3). Each one narrows the list; none is required.
 *
 * @param actor part of the actor's email, ignoring case; {@code LIKE} wildcards in it are matched
 *     literally. A failed sign-in has the email that was typed and no account.
 * @param from entries at or after this instant
 * @param to entries before this instant
 */
public record AuditQuery(
    String actor, AuditAction action, AuditOutcome outcome, Instant from, Instant to) {}

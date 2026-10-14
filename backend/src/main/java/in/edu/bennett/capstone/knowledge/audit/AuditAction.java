package in.edu.bennett.capstone.knowledge.audit;

/**
 * The recorded actions of specification 14.2. The database check constraint lists the same values;
 * {@code AuditServiceTest} checks that each one is accepted.
 */
public enum AuditAction {
  LOGIN_SUCCESS,
  LOGIN_FAILURE,
  LOGOUT,
  USER_REGISTERED,
  ROLE_CHANGED,
  USER_ENABLED,
  USER_DISABLED,
  PASSWORD_CHANGED,
  PASSWORD_RESET,
  DOCUMENT_UPLOADED,
  DOCUMENT_DOWNLOADED,
  DOCUMENT_UPDATED,
  DOCUMENT_DELETED,
  DOCUMENT_REPROCESSED,
  DOCUMENT_ARCHIVED,
  GRANTS_CHANGED,
  ACCESS_DENIED,
  AI_QUERY,
  SUMMARY_REQUESTED,
  COMPARISON_REQUESTED,
  REINDEX_STARTED
}

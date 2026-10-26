package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.user.Role;
import java.util.Comparator;
import java.util.UUID;

/**
 * Whom a grant names: a role, or a user. Exactly one of {@code role} and {@code userId} is set, the
 * one that matches the type; a record, so two equal grants compare equal and a set holds one.
 */
record Grantee(GranteeType type, Role role, UUID userId) {

  /** Role grants first, then user grants, each in a stable order, for responses and audit. */
  static final Comparator<Grantee> ORDER =
      Comparator.comparing(Grantee::type)
          .thenComparing(Grantee::role, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(Grantee::userId, Comparator.nullsLast(Comparator.naturalOrder()));

  Grantee {
    boolean valid =
        type == GranteeType.ROLE
            ? role != null && role != Role.ADMIN && userId == null
            : type == GranteeType.USER && userId != null && role == null;
    if (!valid) {
      throw new IllegalArgumentException(
          "Not a valid grantee: " + type + " " + role + " " + userId);
    }
  }

  static Grantee role(Role role) {
    return new Grantee(GranteeType.ROLE, role, null);
  }

  static Grantee user(UUID userId) {
    return new Grantee(GranteeType.USER, null, userId);
  }

  /** How the grant appears in audit details: the role name, or the user's id. */
  String describe() {
    return type == GranteeType.ROLE ? "ROLE:" + role : "USER:" + userId;
  }
}

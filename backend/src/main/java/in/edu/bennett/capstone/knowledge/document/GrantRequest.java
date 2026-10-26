package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One grant in a request (ACL-1, DOC-8): a role with {@code type} ROLE, or a person with {@code
 * type} USER. Admins read everything (D15), so a role grant names FACULTY or STUDENT.
 */
public record GrantRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull GranteeType type,
    @Schema(description = "For a ROLE grant: FACULTY or STUDENT.") Role role,
    @Schema(description = "For a USER grant: the user's id.") UUID userId) {

  @Schema(hidden = true)
  @AssertTrue(
      message =
          "a ROLE grant names FACULTY or STUDENT and no user; a USER grant names a user and no"
              + " role")
  public boolean isOneGrantee() {
    if (type == null) {
      return true; // @NotNull reports it.
    }
    return type == GranteeType.ROLE
        ? role != null && role != Role.ADMIN && userId == null
        : userId != null && role == null;
  }

  Grantee toGrantee() {
    return new Grantee(type, role, userId);
  }
}

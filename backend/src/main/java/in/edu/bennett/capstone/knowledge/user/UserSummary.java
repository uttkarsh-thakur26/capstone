package in.edu.bennett.capstone.knowledge.user;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/** A user as another user may see them when sharing a document: id, name and email only (DOC-8). */
public record UserSummary(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED) String email) {

  static UserSummary of(UserAccount user) {
    return new UserSummary(user.getId(), user.getDisplayName(), user.getEmail());
  }
}

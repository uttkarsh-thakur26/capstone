package in.edu.bennett.capstone.knowledge.user;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/** An account as its owner sees it. Never carries the password hash (AUTH-4). */
public record UserResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED) Role role) {

  static UserResponse of(UserAccount user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
  }
}

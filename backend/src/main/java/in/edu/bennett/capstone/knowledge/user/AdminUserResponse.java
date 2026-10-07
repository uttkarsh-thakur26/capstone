package in.edu.bennett.capstone.knowledge.user;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

/** An account as an admin sees it in the user list (specification 14.1). No password data. */
public record AdminUserResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED) Role role,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean enabled,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
    Instant lastLoginAt) {

  static AdminUserResponse of(UserAccount user) {
    return new AdminUserResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRole(),
        user.isEnabled(),
        user.getCreatedAt(),
        user.getLastLoginAt());
  }
}

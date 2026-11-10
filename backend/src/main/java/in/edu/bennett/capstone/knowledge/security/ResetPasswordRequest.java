package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** Body of {@code POST /admin/users/{id}/password}: the new password (AUTH-4 rules). */
public record ResetPasswordRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 10, maxLength = 128) @ValidPassword
        String newPassword) {

  @Override
  public String toString() {
    return "ResetPasswordRequest[]";
  }
}

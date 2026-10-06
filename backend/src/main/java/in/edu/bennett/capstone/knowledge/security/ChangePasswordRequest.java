package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /auth/me/password}. */
public record ChangePasswordRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 512) String currentPassword,
    @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 10, maxLength = 128) @ValidPassword
        String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[]";
  }
}

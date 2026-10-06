package in.edu.bennett.capstone.knowledge.user;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /auth/register}. It has no role: self-registration always creates a STUDENT
 * (AUTH-1), and a {@code role} sent by the client is ignored.
 */
public record RegistrationRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Email @Size(max = 254) String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String displayName,
    @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 10, maxLength = 128) @ValidPassword
        String password) {

  public RegistrationRequest {
    // Normalized before validation, so " A@x.org " is accepted and stored as "a@x.org" (AUTH-10).
    email = Emails.normalize(email);
    displayName = displayName == null ? null : displayName.strip();
  }

  @Override
  public String toString() {
    return "RegistrationRequest[email=" + email + ", displayName=" + displayName + "]";
  }
}

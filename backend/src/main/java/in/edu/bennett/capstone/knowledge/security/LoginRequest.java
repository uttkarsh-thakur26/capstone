package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.common.PlainText;
import in.edu.bennett.capstone.knowledge.user.Emails;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /auth/login}. The email is normalized like at registration (AUTH-10), and it
 * must look like an email: a failed sign-in records the email typed (14.2), and a password typed
 * into the email field by mistake must never reach the audit log (AUD-2). Such a request is a 400
 * that records nothing.
 */
public record LoginRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Email @Size(max = 254) @PlainText
        String email,
    @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 512) String password) {

  public LoginRequest {
    email = Emails.normalize(email);
  }

  @Override
  public String toString() {
    return "LoginRequest[email=" + email + "]";
  }
}

package in.edu.bennett.capstone.knowledge.security;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /admin/users/{id}/password} (15.2, 14.1 {@code [T2]}). It lives in this module
 * because ending the user's sessions means revoking refresh tokens, which belong here; the admin
 * rule is the URL rule on {@code /api/v1/admin/**} and method security on {@code
 * UserService.resetPassword} (AUTH-8).
 */
@RestController
class AdminPasswordController {

  private final AuthService auth;

  AdminPasswordController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping(
      path = "/api/v1/admin/users/{id}/password",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> reset(
      @AuthenticationPrincipal CurrentUser admin,
      @PathVariable UUID id,
      @Valid @RequestBody ResetPasswordRequest request) {
    auth.resetPassword(admin, id, request.newPassword());
    return ResponseEntity.noContent().build();
  }
}

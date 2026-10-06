package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;

/**
 * Answer of sign-in and refresh. The SPA keeps the access token in memory only (AUTH-6); the
 * refresh token travels in the {@code HttpOnly} cookie and never in a body.
 */
public record SessionResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) String accessToken,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "Bearer") String tokenType,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant expiresAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) UserResponse user) {

  static SessionResponse of(AuthService.Session session) {
    return new SessionResponse(
        session.accessToken().value(), "Bearer", session.accessToken().expiresAt(), session.user());
  }
}

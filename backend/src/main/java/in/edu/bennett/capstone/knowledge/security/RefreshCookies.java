package in.edu.bennett.capstone.knowledge.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookie (AUTH-6): {@code HttpOnly}, so scripts cannot read it; {@code
 * SameSite=Strict}, so other sites cannot send it; scoped to the auth endpoints; and {@code Secure}
 * except in the {@code dev} profile, which serves plain HTTP on localhost.
 */
@Component
class RefreshCookies {

  static final String NAME = "refresh_token";
  static final String PATH = "/api/v1/auth";

  private final boolean secure;
  private final Clock clock;

  RefreshCookies(SecurityProperties properties, Environment environment, Clock clock) {
    if (!properties.refreshCookieSecure() && !environment.matchesProfiles("dev")) {
      throw new IllegalStateException(
          "app.security.refresh-cookie-secure=false is allowed only in the dev profile (AUTH-6).");
    }
    this.secure = properties.refreshCookieSecure();
    this.clock = clock;
  }

  ResponseCookie create(String value, Instant expiresAt) {
    return base(value).maxAge(Duration.between(clock.instant(), expiresAt)).build();
  }

  ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path(PATH);
  }
}

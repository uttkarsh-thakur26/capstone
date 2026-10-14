package in.edu.bennett.capstone.knowledge.security;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token and cookie settings (specification 18, {@code app.security}). Invalid values stop the
 * application at startup.
 *
 * @param jwtSecret base64 of at least 32 random bytes (256 bits), the HS256 signing key (AUTH-6)
 * @param allowedOrigins origins whose requests may use the refresh cookie (AUTH-9). Never a
 *     wildcard: each entry is compared exactly.
 * @param refreshCookieSecure whether the refresh cookie is marked {@code Secure}. Only the {@code
 *     dev} profile may turn it off (AUTH-6); {@link RefreshCookies} enforces that.
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(
    String jwtSecret,
    @DefaultValue("15m") Duration accessTokenTtl,
    @DefaultValue("7d") Duration refreshTokenTtl,
    List<String> allowedOrigins,
    @DefaultValue("true") boolean refreshCookieSecure) {

  static final int MIN_KEY_BYTES = 32;

  public SecurityProperties {
    decodeKey(jwtSecret);
    allowedOrigins =
        allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .map(
                    origin ->
                        origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .toList();
    if (allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
      throw new IllegalArgumentException(
          "app.security.allowed-origins (APP_SECURITY_ALLOWED_ORIGINS) must list the origins that"
              + " serve the interface, such as http://localhost:3000, and may not be a wildcard.");
    }
  }

  SecretKey signingKey() {
    return new SecretKeySpec(decodeKey(jwtSecret), "HmacSHA256");
  }

  private static byte[] decodeKey(String secret) {
    byte[] key;
    try {
      key = secret == null ? new byte[0] : Base64.getDecoder().decode(secret.strip());
    } catch (IllegalArgumentException e) {
      key = new byte[0];
    }
    if (key.length < MIN_KEY_BYTES) {
      // The message never includes the configured value.
      throw new IllegalArgumentException(
          "app.security.jwt-secret (APP_SECURITY_JWT_SECRET) must be base64 of at least 32 random"
              + " bytes, for example the output of: openssl rand -base64 32");
    }
    return key;
  }

  @Override
  public String toString() {
    return "SecurityProperties[jwtSecret=<set>, accessTokenTtl="
        + accessTokenTtl
        + ", refreshTokenTtl="
        + refreshTokenTtl
        + ", allowedOrigins="
        + allowedOrigins
        + ", refreshCookieSecure="
        + refreshCookieSecure
        + "]";
  }
}

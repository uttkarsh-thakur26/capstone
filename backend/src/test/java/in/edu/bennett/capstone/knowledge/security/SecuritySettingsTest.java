package in.edu.bennett.capstone.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.env.MockEnvironment;

/** The application refuses unsafe security settings at startup (specification 18, AUTH-6). */
class SecuritySettingsTest {

  private static final String GOOD_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
  private static final List<String> ORIGINS = List.of("http://localhost:3000");

  @Test
  void aSigningKeyShorterThan256BitsIsRefused() {
    String shortKey =
        Base64.getEncoder().encodeToString("only-31-bytes-long-key-material".getBytes());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(shortKey, ORIGINS, true))
        .withMessageContaining("at least 32")
        .withMessageNotContaining(shortKey);
  }

  @Test
  void aMissingOrMalformedSigningKeyIsRefused() {
    assertThatIllegalArgumentException().isThrownBy(() -> properties(null, ORIGINS, true));
    assertThatIllegalArgumentException().isThrownBy(() -> properties("change-me", ORIGINS, true));
  }

  @Test
  void originsAreRequiredAndNeverAWildcard() {
    assertThatIllegalArgumentException().isThrownBy(() -> properties(GOOD_KEY, List.of(), true));
    assertThatIllegalArgumentException().isThrownBy(() -> properties(GOOD_KEY, null, true));
    assertThatIllegalArgumentException().isThrownBy(() -> properties(GOOD_KEY, List.of("*"), true));
  }

  @Test
  void originsAreComparedWithoutATrailingSlash() {
    assertThat(properties(GOOD_KEY, List.of(" http://localhost:3000/ "), true).allowedOrigins())
        .containsExactly("http://localhost:3000");
  }

  @Test
  void theKeyIsNeverPrinted() {
    assertThat(properties(GOOD_KEY, ORIGINS, true).toString()).doesNotContain(GOOD_KEY);
  }

  @Test
  void onlyTheDevProfileMayDropSecureFromTheCookie() {
    SecurityProperties insecure = properties(GOOD_KEY, ORIGINS, false);
    Clock clock = Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);

    assertThatIllegalStateException()
        .isThrownBy(() -> new RefreshCookies(insecure, new MockEnvironment(), clock));
    MockEnvironment prod = new MockEnvironment();
    prod.setActiveProfiles("prod");
    assertThatIllegalStateException().isThrownBy(() -> new RefreshCookies(insecure, prod, clock));

    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("dev");
    ResponseCookie cookie =
        new RefreshCookies(insecure, dev, clock).create("value", clock.instant().plusSeconds(60));
    assertThat(cookie.isSecure()).isFalse();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
  }

  @Test
  void theCookieIsScopedToTheAuthPathAndLivesUntilTheTokenExpires() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);
    RefreshCookies cookies =
        new RefreshCookies(properties(GOOD_KEY, ORIGINS, true), new MockEnvironment(), clock);

    ResponseCookie cookie = cookies.create("value", clock.instant().plus(Duration.ofDays(7)));

    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookies.clear().getMaxAge()).isEqualTo(Duration.ZERO);
  }

  private static SecurityProperties properties(
      String key, List<String> origins, boolean secureCookie) {
    return new SecurityProperties(
        key, Duration.ofMinutes(15), Duration.ofDays(7), origins, secureCookie);
  }
}

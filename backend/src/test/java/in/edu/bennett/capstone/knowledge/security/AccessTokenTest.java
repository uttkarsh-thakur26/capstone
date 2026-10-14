package in.edu.bennett.capstone.knowledge.security;

import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.login;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 19.2: missing, expired, tampered and wrongly signed tokens give 401 (AUTH-6). Also tokens with
 * the wrong issuer, no signature algorithm, or a subject that is not an enabled account.
 */
@IntegrationTest
class AccessTokenTest {

  /** The signing key configured in application-test.yaml. */
  private static final byte[] TEST_KEY =
      "test-only-signing-key-not-secret".getBytes(StandardCharsets.US_ASCII);

  @Autowired MockMvcTester mvc;

  private Account account;
  private String validToken;

  @BeforeEach
  void signIn() {
    account = registerStudent(mvc);
    validToken = login(mvc, account.email(), PASSWORD).accessToken();
  }

  @Test
  void aValidTokenIsAccepted() {
    assertThat(call(validToken)).hasStatusOk();
  }

  @Test
  void aMissingTokenGets401() {
    assertUnauthenticated(mvc.get().uri("/api/v1/auth/me").exchange());
  }

  static Stream<Arguments> badTokens() {
    return Stream.of(
        Arguments.of("expired", "expired"),
        Arguments.of("signed with another key", "otherKey"),
        Arguments.of("payload changed after signing", "tampered"),
        Arguments.of("no signature (alg none)", "unsigned"),
        Arguments.of("another issuer", "issuer"),
        Arguments.of("subject is not a user id", "badSubject"),
        Arguments.of("subject is no account", "unknownUser"),
        Arguments.of("not a JWT at all", "garbage"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("badTokens")
  void badTokensGet401(String description, String kind) {
    String token =
        switch (kind) {
          case "expired" ->
              sign(
                  TEST_KEY,
                  JwtConfig.ISSUER,
                  account.id().toString(),
                  Instant.now().minusSeconds(3600),
                  Instant.now().minusSeconds(120));
          case "otherKey" ->
              sign(
                  "another-key-that-is-32-bytes-long".getBytes(StandardCharsets.US_ASCII),
                  JwtConfig.ISSUER,
                  account.id().toString(),
                  Instant.now(),
                  Instant.now().plusSeconds(600));
          // Another real account, so that only the signature can reject the token.
          case "tampered" -> withSubject(validToken, registerStudent(mvc).id().toString());
          case "unsigned" -> unsigned(account.id().toString());
          case "issuer" ->
              sign(
                  TEST_KEY,
                  "someone-else",
                  account.id().toString(),
                  Instant.now(),
                  Instant.now().plusSeconds(600));
          case "badSubject" ->
              sign(
                  TEST_KEY,
                  JwtConfig.ISSUER,
                  "not-a-uuid",
                  Instant.now(),
                  Instant.now().plusSeconds(600));
          case "unknownUser" ->
              sign(
                  TEST_KEY,
                  JwtConfig.ISSUER,
                  UUID.randomUUID().toString(),
                  Instant.now(),
                  Instant.now().plusSeconds(600));
          case "garbage" -> "not.a.jwt";
          default -> throw new IllegalArgumentException(kind);
        };

    assertUnauthenticated(call(token));
  }

  private MvcTestResult call(String token) {
    return mvc.get()
        .uri("/api/v1/auth/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange();
  }

  private static void assertUnauthenticated(MvcTestResult result) {
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    assertThat(result).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
  }

  private static String sign(
      byte[] key, String issuer, String subject, Instant issuedAt, Instant expiresAt) {
    JwtEncoder encoder =
        NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  /** Keeps the header and signature of a real token and swaps in another subject. */
  private static String withSubject(String token, String subject) {
    String[] parts = token.split("\\.");
    String payload =
        new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replaceAll("\"sub\":\"[^\"]+\"", "\"sub\":\"" + subject + "\"");
    return parts[0] + "." + encode(payload) + "." + parts[2];
  }

  private static String unsigned(String subject) {
    long exp = Instant.now().plusSeconds(600).getEpochSecond();
    return encode("{\"alg\":\"none\"}")
        + "."
        + encode(
            "{\"iss\":\"%s\",\"sub\":\"%s\",\"exp\":%d}".formatted(JwtConfig.ISSUER, subject, exp))
        + ".";
  }

  private static String encode(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }
}

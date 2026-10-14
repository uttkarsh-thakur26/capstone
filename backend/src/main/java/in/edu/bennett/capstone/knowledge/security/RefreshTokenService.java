package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.UserResponse;
import in.edu.bennett.capstone.knowledge.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens: 32 random bytes, stored as their SHA-256, valid 7 days, rotated on every use
 * (AUTH-6). A slow hash such as Argon2id would add nothing: the token is found by its hash, which
 * must therefore be deterministic, and 256 random bits cannot be guessed (ADR-020).
 */
@Service
class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository tokens;
  private final UserService users;
  private final SecurityProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  RefreshTokenService(
      RefreshTokenRepository tokens,
      UserService users,
      SecurityProperties properties,
      Clock clock) {
    this.tokens = tokens;
    this.users = users;
    this.properties = properties;
    this.clock = clock;
  }

  /** A new token that starts a new family: one per sign-in. */
  @Transactional
  public IssuedToken issueForNewSignIn(UUID userId) {
    return issue(userId, UUID.randomUUID());
  }

  /**
   * Exchanges a token for a new one in the same family. A token that was already rotated or revoked
   * means two clients hold it, one of them with a stolen copy, so the whole family is revoked (RFC
   * 9700, ADR-020). The method returns instead of throwing, so that revocation is committed.
   */
  @Transactional
  public Optional<Rotation> rotate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    Optional<RefreshToken> found = tokens.findByTokenHashForUpdate(hash(rawToken));
    if (found.isEmpty()) {
      return Optional.empty();
    }
    RefreshToken token = found.get();
    Instant now = clock.instant();
    if (token.isRevoked()) {
      int revoked = tokens.revokeFamily(token.getFamilyId(), now);
      log.warn(
          "A rotated refresh token of user {} was presented again; revoked {} token(s) of its"
              + " family",
          token.getUserId(),
          revoked);
      return Optional.empty();
    }
    if (!token.getExpiresAt().isAfter(now)) {
      return Optional.empty();
    }
    Optional<UserResponse> user = users.findEnabled(token.getUserId());
    if (user.isEmpty()) {
      tokens.revokeFamily(token.getFamilyId(), now);
      return Optional.empty();
    }
    token.revoke(now);
    IssuedToken next = issue(token.getUserId(), token.getFamilyId());
    return Optional.of(new Rotation(user.get(), next));
  }

  /**
   * Ends the sign-in that the token belongs to, whether or not the token is still current.
   *
   * @return the token's user, if the token was known
   */
  @Transactional
  public Optional<UUID> revokeSignIn(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return tokens
        .findByTokenHash(hash(rawToken))
        .map(
            token -> {
              tokens.revokeFamily(token.getFamilyId(), clock.instant());
              return token.getUserId();
            });
  }

  @Transactional
  public void revokeAllOfUser(UUID userId) {
    tokens.revokeAllOfUser(userId, clock.instant());
  }

  private IssuedToken issue(UUID userId, UUID familyId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
    tokens.save(new RefreshToken(userId, hash(raw), familyId, expiresAt));
    return new IssuedToken(raw, expiresAt);
  }

  static String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java platform", e);
    }
  }

  /** A token value for the cookie. The value exists only here and in the client. */
  record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
      return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
  }

  record Rotation(UserResponse user, IssuedToken token) {}
}

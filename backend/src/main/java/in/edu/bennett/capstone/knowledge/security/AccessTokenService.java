package in.edu.bennett.capstone.knowledge.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues access tokens: 15 minutes, carrying only the issuer and the user's id (ADR-020). */
@Service
class AccessTokenService {

  private final JwtEncoder encoder;
  private final SecurityProperties properties;
  private final Clock clock;

  AccessTokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
  }

  AccessToken issue(UUID userId) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(JwtConfig.ISSUER)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();
    String value =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new AccessToken(value, expiresAt);
  }

  record AccessToken(String value, Instant expiresAt) {}
}

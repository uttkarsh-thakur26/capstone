package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.UserService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access tokens are HS256 JWTs signed and checked with Spring Security's Nimbus support (AUTH-6,
 * ADR-020). The same application signs and verifies, so a shared secret is enough.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class JwtConfig {

  static final String ISSUER = "knowledge-platform";

  /** Tolerance for clocks that differ slightly; kept small because tokens live 15 minutes. */
  private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  @Bean
  Converter<Jwt, AbstractAuthenticationToken> currentUserConverter(UserService users) {
    return new CurrentUserJwtConverter(users);
  }

  @Bean
  JwtEncoder jwtEncoder(SecurityProperties properties) {
    return NimbusJwtEncoder.withSecretKey(properties.signingKey())
        .algorithm(MacAlgorithm.HS256)
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(SecurityProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(properties.signingKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    JwtTimestampValidator expiry = new JwtTimestampValidator(CLOCK_SKEW);
    expiry.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(expiry, new JwtIssuerValidator(ISSUER)));
    return decoder;
  }
}

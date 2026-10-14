package in.edu.bennett.capstone.knowledge.security;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AUTH-4, ADR-022: new hashes use Argon2id. The delegating encoder stores an {@code {id}} prefix
 * with each hash, so the algorithm or its parameters can change later without breaking existing
 * ones. bcrypt is not offered: in Spring Security 7.1.1 it rejects passwords over 72 bytes.
 */
@Configuration(proxyBeanMethods = false)
class PasswordConfig {

  static final String ARGON2_ID = "argon2@SpringSecurity_v5_8";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        ARGON2_ID, Map.of(ARGON2_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
  }
}

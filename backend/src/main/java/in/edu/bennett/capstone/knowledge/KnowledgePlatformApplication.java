package in.edu.bennett.capstone.knowledge;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Boot's default in-memory user with a generated password is never wanted. Accounts arrive in
// Phase 1 with their own user store.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class KnowledgePlatformApplication {

  public static void main(String[] args) {
    // All times are UTC (specification 4.5). The JDBC driver sends the JVM's zone id to
    // PostgreSQL, and a host zone such as the legacy "Asia/Calcutta" is rejected by the
    // database image, so the JVM never runs in the host's zone (ADR-016).
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(KnowledgePlatformApplication.class, args);
  }
}

package in.edu.bennett.capstone.knowledge.user;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Self-registration settings (specification 18, {@code app.registration}).
 *
 * @param allowedEmailDomains when not empty, only emails in these domains may register (AUTH-1).
 *     Empty by default: anyone may register as a STUDENT.
 */
@ConfigurationProperties("app.registration")
public record RegistrationProperties(List<String> allowedEmailDomains) {

  public RegistrationProperties {
    allowedEmailDomains =
        allowedEmailDomains == null
            ? List.of()
            : allowedEmailDomains.stream()
                .map(String::strip)
                .filter(domain -> !domain.isEmpty())
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .toList();
  }

  /** Whether an already normalized email may register. Subdomains must be listed themselves. */
  boolean allows(String email) {
    if (allowedEmailDomains.isEmpty()) {
      return true;
    }
    int at = email.lastIndexOf('@');
    return at >= 0 && allowedEmailDomains.contains(email.substring(at + 1));
  }
}

package in.edu.bennett.capstone.knowledge.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The first admin account, created at startup only while no admin exists (AUTH-3). There is no
 * default email or password: without them, a database with no admin stops the application.
 */
@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(
    String email, String password, @DefaultValue("Administrator") String displayName) {

  public BootstrapAdminProperties {
    email = email == null || email.isBlank() ? null : Emails.normalize(email);
    password = password == null || password.isEmpty() ? null : password;
  }

  /** Never prints the password, which a failure report or a debug log might otherwise show. */
  @Override
  public String toString() {
    return "BootstrapAdminProperties[email="
        + email
        + ", password="
        + (password == null ? "<not set>" : "<set>")
        + ", displayName="
        + displayName
        + "]";
  }
}

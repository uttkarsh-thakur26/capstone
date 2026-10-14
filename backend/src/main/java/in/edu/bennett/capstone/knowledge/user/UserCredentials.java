package in.edu.bennett.capstone.knowledge.user;

import java.util.UUID;

/**
 * What sign-in needs to check a password. Only the {@code security} module uses it, and only inside
 * the authentication step.
 */
public record UserCredentials(
    UUID id, String email, String passwordHash, Role role, boolean enabled) {

  @Override
  public String toString() {
    return "UserCredentials[id=" + id + ", role=" + role + ", enabled=" + enabled + "]";
  }
}

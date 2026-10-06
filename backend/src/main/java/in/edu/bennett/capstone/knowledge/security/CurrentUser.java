package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.user.Role;
import in.edu.bennett.capstone.knowledge.user.UserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated principal, loaded from the database for this request (AUTH-7). Access decisions
 * take the user's id and role from here only, never from the request (ACL-E4).
 */
public record CurrentUser(UUID id, String email, String displayName, Role role) {

  static CurrentUser of(UserResponse user) {
    return new CurrentUser(user.id(), user.email(), user.displayName(), user.role());
  }

  List<GrantedAuthority> authorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  public UserResponse toResponse() {
    return new UserResponse(id, email, displayName, role);
  }
}

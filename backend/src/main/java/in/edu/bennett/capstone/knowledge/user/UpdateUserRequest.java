package in.edu.bennett.capstone.knowledge.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/** Body of {@code PATCH /admin/users/{id}}: a new role, a new enabled flag, or both (AUTH-2). */
public record UpdateUserRequest(Role role, Boolean enabled) {

  @Schema(hidden = true)
  @AssertTrue(message = "must change role, enabled or both")
  public boolean isAnyChange() {
    return role != null || enabled != null;
  }
}

package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration for admins (specification 14.1, AUTH-2). The URL rule in {@code
 * SecurityConfig} and method security on {@link UserService} both require ADMIN (AUTH-8).
 */
@RestController
class AdminUserController {

  private final UserService users;

  AdminUserController(UserService users) {
    this.users = users;
  }

  @GetMapping(path = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
  PageResponse<AdminUserResponse> list(
      @RequestParam(required = false) @Size(max = 254) String query,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_SIZE) int size,
      @RequestParam(required = false) String sort) {
    return users.listUsers(new UserQuery(query, role, enabled), page, size, sort);
  }

  /**
   * The acting admin's id comes from the authenticated principal only (ACL-E4). It is read by
   * expression, so this module needs no compile-time dependency on the {@code security} module.
   */
  @PatchMapping(
      path = "/api/v1/admin/users/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  AdminUserResponse update(
      @AuthenticationPrincipal(expression = "id()") UUID actorId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest change) {
    return users.updateUser(actorId, id, change);
  }
}

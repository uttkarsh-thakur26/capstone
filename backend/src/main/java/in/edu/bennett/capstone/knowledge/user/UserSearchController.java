package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finding people to share a document with (DOC-8). Faculty and admins only, by the URL rule in
 * {@code SecurityConfig} and by method security on {@link UserService#searchUsers}.
 */
@RestController
class UserSearchController {

  private final UserService users;

  UserSearchController(UserService users) {
    this.users = users;
  }

  @GetMapping(path = "/api/v1/users/search", produces = MediaType.APPLICATION_JSON_VALUE)
  PageResponse<UserSummary> search(
      @RequestParam @Size(max = 254) String query,
      @RequestParam(defaultValue = "0") @Min(0) @Max(PageResponse.MAX_PAGE) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
    return users.searchUsers(query, page, size);
  }
}

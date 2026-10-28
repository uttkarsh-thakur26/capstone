package in.edu.bennett.capstone.knowledge.retrieval;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /search} (specification 15.2), for every signed-in user. The user comes from the
 * authenticated principal (ACL-E4); {@link SearchService} applies every rule.
 */
@RestController
class SearchController {

  private final SearchService search;

  SearchController(SearchService search) {
    this.search = search;
  }

  @PostMapping(
      path = "/api/v1/search",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  SearchResponse search(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SearchRequest request) {
    return search.search(user, request);
  }
}

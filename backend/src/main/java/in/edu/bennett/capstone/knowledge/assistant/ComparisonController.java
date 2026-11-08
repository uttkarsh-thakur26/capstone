package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Comparing two documents (15.2, Section 13.2): any user who can read both. */
@RestController
class ComparisonController {

  private final ComparisonService comparisons;

  ComparisonController(ComparisonService comparisons) {
    this.comparisons = comparisons;
  }

  @PostMapping(
      path = "/api/v1/documents/compare",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ComparisonResponse compare(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ComparisonRequest request) {
    return comparisons.compare(user, request);
  }
}

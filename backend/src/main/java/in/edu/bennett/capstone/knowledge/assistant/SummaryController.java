package in.edu.bennett.capstone.knowledge.assistant;

import in.edu.bennett.capstone.knowledge.assistant.SummaryResponse.Status;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** A document's summary (15.2, Section 13.1): any user who can read the document. */
@RestController
class SummaryController {

  private final SummaryService summaries;

  SummaryController(SummaryService summaries) {
    this.summaries = summaries;
  }

  /** Starts a summary: 202 while it is being made, 200 when a fresh one is ready. */
  @PostMapping(path = "/api/v1/documents/{id}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SummaryResponse> request(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    SummaryResponse response = summaries.request(user, id);
    return ResponseEntity.status(
            response.status() == Status.READY ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(response);
  }

  /** The summary's status and, once ready, its content (SUM-5); the screen asks again. */
  @GetMapping(path = "/api/v1/documents/{id}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  SummaryResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    return summaries.get(user, id);
  }
}

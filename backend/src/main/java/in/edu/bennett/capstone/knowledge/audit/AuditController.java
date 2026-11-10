package in.edu.bennett.capstone.knowledge.audit;

import in.edu.bennett.capstone.knowledge.common.PageResponse;
import in.edu.bennett.capstone.knowledge.common.PlainText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /admin/audit} (specification 15.2, AUD-3): admins only, by the URL rule on {@code
 * /api/v1/admin/**} and by method security on {@link AuditLogReader}.
 */
@RestController
class AuditController {

  private final AuditLogReader reader;

  AuditController(AuditLogReader reader) {
    this.reader = reader;
  }

  /**
   * @param actor part of the actor's email
   * @param from ISO 8601 instant, inclusive
   * @param to ISO 8601 instant, exclusive
   */
  @GetMapping(path = "/api/v1/admin/audit", produces = MediaType.APPLICATION_JSON_VALUE)
  PageResponse<AuditEntryResponse> list(
      @RequestParam(required = false) @Size(max = 254) @PlainText String actor,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) AuditOutcome outcome,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") @Min(0) @Max(PageResponse.MAX_PAGE) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
    return reader.list(new AuditQuery(actor, action, outcome, from, to), page, size);
  }
}

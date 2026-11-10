package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.common.PageResponse;
import in.edu.bennett.capstone.knowledge.common.PlainText;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document administration (14.1, 15.2): admins only, by the URL rule on {@code /api/v1/admin/**}
 * and by method security on {@link DocumentService}. Reprocessing, deleting and sharing one
 * document use the document endpoints, which already allow admins (ACL-2).
 */
@RestController
class AdminDocumentController {

  private final DocumentService documents;

  AdminDocumentController(DocumentService documents) {
    this.documents = documents;
  }

  @GetMapping(path = "/api/v1/admin/documents", produces = MediaType.APPLICATION_JSON_VALUE)
  PageResponse<AdminDocumentResponse> list(
      @AuthenticationPrincipal CurrentUser admin,
      @RequestParam(required = false) @Size(max = 200) @PlainText String query,
      @RequestParam(required = false) DocumentStatus status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(PageResponse.MAX_PAGE) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_SIZE) int size,
      @RequestParam(required = false) @Size(max = 64) String sort) {
    return documents.listForAdmin(
        admin, new DocumentQuery(query, status, null, false, null), page, size, sort);
  }

  /** Answers 202: the documents are queued, and the worker processes them over time. */
  @PostMapping(path = "/api/v1/admin/reindex", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ReindexResponse> reindex(@AuthenticationPrincipal CurrentUser admin) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(documents.reindexAll(admin));
  }
}

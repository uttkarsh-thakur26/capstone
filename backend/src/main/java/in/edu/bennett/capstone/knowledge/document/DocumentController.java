package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.common.PageResponse;
import in.edu.bennett.capstone.knowledge.common.PlainText;
import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document endpoints (specification 15.2). The user always comes from the authenticated principal
 * (ACL-E4); every rule is applied by {@link DocumentService}.
 */
@RestController
class DocumentController {

  private final DocumentService documents;

  DocumentController(DocumentService documents) {
    this.documents = documents;
  }

  /** DOC-1: the file and a JSON {@code metadata} part (ADR-031). ADMIN and FACULTY only. */
  @PostMapping(
      path = "/api/v1/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<DocumentResponse> upload(
      @AuthenticationPrincipal CurrentUser user,
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("metadata") UploadDocumentRequest metadata) {
    DocumentResponse created = documents.upload(user, file, metadata);
    return ResponseEntity.created(URI.create("/api/v1/documents/" + created.id())).body(created);
  }

  @GetMapping(path = "/api/v1/documents/upload-limits", produces = MediaType.APPLICATION_JSON_VALUE)
  UploadLimitsResponse uploadLimits() {
    return documents.uploadLimits();
  }

  /** DOC-4: readable documents only, with filters, paging and sorting. */
  @GetMapping(path = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
  PageResponse<DocumentSummaryResponse> list(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(required = false) @Size(max = 200) @PlainText String query,
      @RequestParam(required = false) DocumentStatus status,
      @RequestParam(required = false) DocumentCategory category,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(required = false) Boolean archived,
      @RequestParam(defaultValue = "0") @Min(0) @Max(PageResponse.MAX_PAGE) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_SIZE) int size,
      @RequestParam(required = false) @Size(max = 64) String sort) {
    return documents.list(
        user, new DocumentQuery(query, status, category, mine, archived), page, size, sort);
  }

  /** DOC-5. A document the user cannot read answers 404, like one that does not exist (ACL-8). */
  @GetMapping(path = "/api/v1/documents/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  DocumentResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    return documents.get(user, id);
  }

  /** DOC-7, DOC-11: owner or admin (ACL-2). A reader who cannot manage it gets 403. */
  @PatchMapping(
      path = "/api/v1/documents/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  DocumentResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateDocumentRequest change) {
    return documents.update(user, id, change);
  }

  /** DOC-10: back to {@code UPLOADED} from {@code FAILED} or {@code INDEXED}. */
  @PostMapping(
      path = "/api/v1/documents/{id}/reprocess",
      produces = MediaType.APPLICATION_JSON_VALUE)
  DocumentResponse reprocess(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    return documents.reprocess(user, id);
  }

  /** DOC-8: owner and admins only (ACL-2). */
  @GetMapping(path = "/api/v1/documents/{id}/grants", produces = MediaType.APPLICATION_JSON_VALUE)
  GrantsResponse grants(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    return documents.grants(user, id);
  }

  /** DOC-8: replaces the whole set; it applies to everyone's next request (ACL-7). */
  @PutMapping(
      path = "/api/v1/documents/{id}/grants",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  GrantsResponse replaceGrants(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateGrantsRequest request) {
    return documents.replaceGrants(user, id, request);
  }

  /** DOC-9: the rows now, the stored file right after the commit. */
  @DeleteMapping("/api/v1/documents/{id}")
  ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    documents.delete(user, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * DOC-6: streams the original file. The filename is the sanitized one from upload, encoded by
   * {@link ContentDisposition}; {@code nosniff} stops a browser from treating it as anything but a
   * PDF, and {@code no-store} keeps a copy out of caches, so a revoked grant cannot be served from
   * one (ACL-7).
   */
  @GetMapping("/api/v1/documents/{id}/content")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PDF_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  void content(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID id,
      HttpServletResponse response)
      throws IOException {
    DocumentFile file = documents.download(user, id);
    try (InputStream content = file.content()) {
      response.setContentType(file.contentType());
      response.setContentLengthLong(file.sizeBytes());
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION,
          ContentDisposition.attachment()
              .filename(file.filename(), StandardCharsets.UTF_8)
              .build()
              .toString());
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      content.transferTo(response.getOutputStream());
    }
  }
}

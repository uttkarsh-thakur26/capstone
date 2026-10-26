package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}

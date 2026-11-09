package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the document list (DOC-4). */
public record DocumentSummaryResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String title,
    DocumentCategory category,
    LocalDate documentDate,
    @Schema(requiredMode = RequiredMode.REQUIRED) DocumentStatus status,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean archived,
    @Schema(requiredMode = RequiredMode.REQUIRED) OwnerResponse owner,
    @Schema(requiredMode = RequiredMode.REQUIRED) long sizeBytes,
    Integer pageCount,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt) {

  static DocumentSummaryResponse of(Document document, String ownerName) {
    return new DocumentSummaryResponse(
        document.getId(),
        document.getTitle(),
        document.getCategory(),
        document.getDocumentDate(),
        document.getStatus(),
        document.isArchived(),
        new OwnerResponse(document.getOwnerId(), ownerName),
        document.getSizeBytes(),
        document.getPageCount(),
        document.getCreatedAt());
  }
}

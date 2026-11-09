package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A document's detail (DOC-5). Grants are not part of it: they are read from {@code
 * /documents/{id}/grants} by the owner and admins only, and {@code canManage} tells the interface
 * whether to offer that and the other manage actions (ACL-2).
 */
public record DocumentResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String title,
    String description,
    DocumentCategory category,
    LocalDate documentDate,
    @Schema(requiredMode = RequiredMode.REQUIRED) String originalFilename,
    @Schema(requiredMode = RequiredMode.REQUIRED) String contentType,
    @Schema(requiredMode = RequiredMode.REQUIRED) long sizeBytes,
    Integer pageCount,
    Integer chunkCount,
    @Schema(requiredMode = RequiredMode.REQUIRED) DocumentStatus status,
    FailureCode failureCode,
    @Schema(description = "Why processing failed, in words a user can read.") String failureMessage,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean archived,
    @Schema(requiredMode = RequiredMode.REQUIRED) OwnerResponse owner,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant updatedAt,
    Instant indexedAt,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean canManage) {

  static DocumentResponse of(Document document, String ownerName, boolean canManage) {
    FailureCode failure = document.getFailureCode();
    return new DocumentResponse(
        document.getId(),
        document.getTitle(),
        document.getDescription(),
        document.getCategory(),
        document.getDocumentDate(),
        document.getOriginalFilename(),
        document.getContentType(),
        document.getSizeBytes(),
        document.getPageCount(),
        document.getChunkCount(),
        document.getStatus(),
        failure,
        failure == null
            ? null
            : document.getFailureMessage() != null
                ? document.getFailureMessage()
                : failure.message(),
        document.isArchived(),
        new OwnerResponse(document.getOwnerId(), ownerName),
        document.getCreatedAt(),
        document.getUpdatedAt(),
        document.getIndexedAt(),
        canManage);
  }
}

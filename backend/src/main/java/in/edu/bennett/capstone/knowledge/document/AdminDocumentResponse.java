package in.edu.bennett.capstone.knowledge.document;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin document list (14.1): the status with the reason a document failed, and how
 * it was processed. The failure message is one the application wrote, never text from the file.
 */
public record AdminDocumentResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = RequiredMode.REQUIRED) OwnerResponse owner,
    @Schema(requiredMode = RequiredMode.REQUIRED) DocumentStatus status,
    FailureCode failureCode,
    String failureMessage,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean archived,
    Integer pageCount,
    Integer chunkCount,
    @Schema(requiredMode = RequiredMode.REQUIRED) int processingAttempts,
    String embeddingModel,
    @Schema(requiredMode = RequiredMode.REQUIRED) long sizeBytes,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
    Instant indexedAt) {

  static AdminDocumentResponse of(Document document, String ownerName) {
    return new AdminDocumentResponse(
        document.getId(),
        document.getTitle(),
        new OwnerResponse(document.getOwnerId(), ownerName),
        document.getStatus(),
        document.getFailureCode(),
        document.getFailureMessage(),
        document.isArchived(),
        document.getPageCount(),
        document.getChunkCount(),
        document.getProcessingAttempts(),
        document.getEmbeddingModel(),
        document.getSizeBytes(),
        document.getCreatedAt(),
        document.getIndexedAt());
  }
}

package in.edu.bennett.capstone.knowledge.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An uploaded document (table {@code document}, specification 7.2). Never leaves the {@code
 * document} module (4.3). The owner is an id, not an entity of the {@code user} module.
 *
 * <p>{@code row_version} makes two requests that change the same document at the same time fail
 * instead of overwriting each other (DATA-3). The content is immutable (DOC-7): the file's name,
 * type, size, hash and key are set once.
 */
@Entity
@Table(name = "document")
@EntityListeners(AuditingEntityListener.class)
@DynamicUpdate
class Document {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String title;

  private String description;

  @Enumerated(EnumType.STRING)
  private DocumentCategory category;

  private LocalDate documentDate;

  @Column(nullable = false, updatable = false)
  private String originalFilename;

  @Column(nullable = false, updatable = false)
  private String contentType;

  @Column(nullable = false, updatable = false)
  private long sizeBytes;

  @Column(nullable = false, updatable = false)
  private String sha256;

  @Column(nullable = false, updatable = false)
  private String storageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Enumerated(EnumType.STRING)
  private FailureCode failureCode;

  private String failureMessage;

  private Integer pageCount;

  private Integer chunkCount;

  private boolean archived;

  private int processingAttempts;

  private Instant processingStartedAt;

  private Instant indexedAt;

  private String embeddingModel;

  private Integer embeddingDim;

  @Version private long rowVersion;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  protected Document() {}

  /** A new upload: {@code UPLOADED}, not archived, never processed (DOC-3). */
  Document(UUID ownerId, DocumentMetadata metadata, StoredUpload file) {
    this.ownerId = ownerId;
    describe(metadata);
    this.originalFilename = file.filename();
    this.contentType = file.contentType();
    this.sizeBytes = file.sizeBytes();
    this.sha256 = file.sha256();
    this.storageKey = file.storageKey();
    this.pageCount = file.pageCount();
    this.status = DocumentStatus.UPLOADED;
  }

  /** DOC-7: title, description, category and document date are replaced together. */
  void describe(DocumentMetadata metadata) {
    this.title = metadata.title();
    this.description = metadata.description();
    this.category = metadata.category();
    this.documentDate = metadata.documentDate();
  }

  void setArchived(boolean archived) {
    this.archived = archived;
  }

  /**
   * DOC-10: back to {@code UPLOADED} with a fresh attempt counter, the failure cleared. The caller
   * has checked {@link DocumentStatus#canBeReprocessed()}; the status machine checks again.
   */
  void reprocess() {
    moveTo(DocumentStatus.UPLOADED);
    this.failureCode = null;
    this.failureMessage = null;
    this.processingAttempts = 0;
    this.processingStartedAt = null;
    this.indexedAt = null;
  }

  private void moveTo(DocumentStatus next) {
    if (!status.canBecome(next)) {
      throw new IllegalStateException(
          "Illegal status transition " + status + " -> " + next + " for document " + id);
    }
    this.status = next;
  }

  UUID getId() {
    return id;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  String getTitle() {
    return title;
  }

  String getDescription() {
    return description;
  }

  DocumentCategory getCategory() {
    return category;
  }

  LocalDate getDocumentDate() {
    return documentDate;
  }

  String getOriginalFilename() {
    return originalFilename;
  }

  String getContentType() {
    return contentType;
  }

  long getSizeBytes() {
    return sizeBytes;
  }

  String getStorageKey() {
    return storageKey;
  }

  DocumentStatus getStatus() {
    return status;
  }

  FailureCode getFailureCode() {
    return failureCode;
  }

  String getFailureMessage() {
    return failureMessage;
  }

  Integer getPageCount() {
    return pageCount;
  }

  Integer getChunkCount() {
    return chunkCount;
  }

  boolean isArchived() {
    return archived;
  }

  int getProcessingAttempts() {
    return processingAttempts;
  }

  String getEmbeddingModel() {
    return embeddingModel;
  }

  Instant getIndexedAt() {
    return indexedAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}

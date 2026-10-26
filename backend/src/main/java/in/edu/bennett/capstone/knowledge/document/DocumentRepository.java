package in.edu.bennett.capstone.knowledge.document;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Package-private: other modules go through {@link DocumentService} (4.3). Queries that return
 * documents to a user take the readable predicate of {@link DocumentAccessPolicy} as a {@code
 * Specification}, so the check is part of their SQL (ACL-E2). The methods here without it are for
 * the uploader's own duplicate check and for locking a row already found through it.
 */
interface DocumentRepository
    extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

  /** DOC-2: the owner's earlier upload of the same content, if any. */
  Optional<Document> findByOwnerIdAndSha256(UUID ownerId, String sha256);

  /**
   * Locks the row until the transaction ends, so two replacements of one document's grants run one
   * after the other. Called only after the readable check found the document.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Document d where d.id = :id")
  Optional<Document> lockById(UUID id);
}

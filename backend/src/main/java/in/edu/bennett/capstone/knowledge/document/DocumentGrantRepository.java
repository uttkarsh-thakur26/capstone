package in.edu.bennett.capstone.knowledge.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Package-private (4.3). Used only after the document itself passed the access check (ACL-E1). */
interface DocumentGrantRepository extends JpaRepository<DocumentGrant, UUID> {

  List<DocumentGrant> findByDocumentId(UUID documentId);

  /** Removes every grant of the document in one statement, before the new set is inserted. */
  @Modifying(flushAutomatically = true)
  @Query("delete from DocumentGrant g where g.documentId = :documentId")
  void deleteByDocumentId(UUID documentId);
}

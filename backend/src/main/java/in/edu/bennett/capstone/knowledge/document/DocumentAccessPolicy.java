package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import org.springframework.data.jpa.domain.Specification;

/**
 * Who may read and who may manage a document (ACL-1, ACL-2), behind an interface (4.4). The user's
 * id and role come from the authenticated principal only (ACL-E4).
 */
interface DocumentAccessPolicy {

  /**
   * The readable predicate for JPA queries, defined once (ACL-E3). It becomes part of the SQL
   * {@code WHERE} clause of every query that uses it, so documents the user cannot read are never
   * loaded, counted or paged (ACL-E2, ACL-8).
   */
  Specification<Document> readableBy(CurrentUser user);

  /** ACL-2: may the user edit, share, reprocess, archive or delete a document they can read? */
  boolean canManage(Document document, CurrentUser user);
}

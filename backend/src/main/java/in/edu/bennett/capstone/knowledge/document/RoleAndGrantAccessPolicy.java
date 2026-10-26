package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.security.CurrentUser;
import in.edu.bennett.capstone.knowledge.user.Role;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * The rules of specification 5.2. The SQL that {@link #readableBy} produces for a non-admin is the
 * readable predicate of 5.3:
 *
 * <pre>
 * d.owner_id = :uid
 * OR EXISTS (SELECT 1 FROM document_grant g
 *            WHERE g.document_id = d.id
 *              AND ((g.grantee_type = 'ROLE' AND g.grantee_role = :role)
 *                OR (g.grantee_type = 'USER' AND g.grantee_user_id = :uid)))
 * </pre>
 *
 * Admins read every document (D15), so for them the predicate adds no condition. The enabled flag
 * is not part of it: a disabled user cannot authenticate at all (AUTH-7).
 */
@Component
class RoleAndGrantAccessPolicy implements DocumentAccessPolicy {

  @Override
  public Specification<Document> readableBy(CurrentUser user) {
    if (user.role() == Role.ADMIN) {
      return Specification.unrestricted();
    }
    return (document, query, cb) -> {
      Subquery<Integer> grant = query.subquery(Integer.class);
      Root<DocumentGrant> g = grant.from(DocumentGrant.class);
      grant
          .select(cb.literal(1))
          .where(
              cb.equal(g.get("documentId"), document.get("id")),
              cb.or(
                  cb.and(
                      cb.equal(g.get("granteeType"), GranteeType.ROLE),
                      cb.equal(g.get("granteeRole"), user.role())),
                  cb.and(
                      cb.equal(g.get("granteeType"), GranteeType.USER),
                      cb.equal(g.get("granteeUserId"), user.id()))));
      return cb.or(cb.equal(document.get("ownerId"), user.id()), cb.exists(grant));
    };
  }

  /**
   * ACL-2 with ADR-030: admins, and the owner while the owner is FACULTY. An owner demoted to
   * STUDENT still reads the document through the readable predicate, but no longer manages it.
   */
  @Override
  public boolean canManage(Document document, CurrentUser user) {
    return user.role() == Role.ADMIN
        || (user.role() == Role.FACULTY && document.getOwnerId().equals(user.id()));
  }
}

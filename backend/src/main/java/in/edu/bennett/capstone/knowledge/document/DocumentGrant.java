package in.edu.bennett.capstone.knowledge.document;

import in.edu.bennett.capstone.knowledge.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One grant of read access to a role or to a person (ACL-1, table {@code document_grant}). Grants
 * are replaced as a set and never changed (ADR-030), so the entity is immutable.
 */
@Entity
@Table(name = "document_grant")
@EntityListeners(AuditingEntityListener.class)
@Immutable
class DocumentGrant {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private GranteeType granteeType;

  @Enumerated(EnumType.STRING)
  private Role granteeRole;

  private UUID granteeUserId;

  private UUID createdBy;

  @CreatedDate
  @Column(nullable = false)
  private Instant createdAt;

  protected DocumentGrant() {}

  DocumentGrant(UUID documentId, Grantee grantee, UUID createdBy) {
    this.documentId = documentId;
    this.granteeType = grantee.type();
    this.granteeRole = grantee.role();
    this.granteeUserId = grantee.userId();
    this.createdBy = createdBy;
  }

  Grantee grantee() {
    return new Grantee(granteeType, granteeRole, granteeUserId);
  }
}

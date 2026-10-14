package in.edu.bennett.capstone.knowledge.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A stored refresh token: only its SHA-256, never the value (AUTH-6). The user is referenced by id,
 * because the account entity belongs to the {@code user} module (4.3).
 */
@Entity
@Table(name = "refresh_token")
@EntityListeners(AuditingEntityListener.class)
class RefreshToken {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, updatable = false)
  private String tokenHash;

  @Column(nullable = false, updatable = false)
  private UUID familyId;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  private Instant revokedAt;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  protected RefreshToken() {}

  RefreshToken(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
  }

  UUID getUserId() {
    return userId;
  }

  UUID getFamilyId() {
    return familyId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  boolean isRevoked() {
    return revokedAt != null;
  }

  void revoke(Instant when) {
    this.revokedAt = when;
  }
}

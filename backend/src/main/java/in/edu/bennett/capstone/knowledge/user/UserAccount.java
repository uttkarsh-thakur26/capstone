package in.edu.bennett.capstone.knowledge.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An account (table {@code app_user}). Never leaves the {@code user} module (4.3).
 *
 * <p>Updates write only the columns that changed. Writing every column would let a request that
 * read the row earlier, such as a sign-in or a password change, put back the role or enabled flag
 * that an admin changed in the meantime (AUTH-2, AUTH-7).
 */
@Entity
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
@DynamicUpdate
class UserAccount {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  private boolean enabled;

  private Instant lastLoginAt;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  protected UserAccount() {}

  UserAccount(String email, String displayName, String passwordHash, Role role) {
    this.email = email;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
  }

  UUID getId() {
    return id;
  }

  String getEmail() {
    return email;
  }

  String getDisplayName() {
    return displayName;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  Role getRole() {
    return role;
  }

  boolean isEnabled() {
    return enabled;
  }

  Instant getLastLoginAt() {
    return lastLoginAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void recordLogin(Instant when) {
    this.lastLoginAt = when;
  }

  void changePasswordHash(String newHash) {
    this.passwordHash = newHash;
  }

  void changeRole(Role newRole) {
    this.role = newRole;
  }

  void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}

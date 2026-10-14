package in.edu.bennett.capstone.knowledge.security;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /**
   * Locks the row, so two requests presenting the same token are handled one after the other: the
   * second sees the token already rotated and is treated as reuse.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

  Optional<RefreshToken> findByTokenHash(String hash);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now"
          + " where t.familyId = :family and t.revokedAt is null")
  int revokeFamily(@Param("family") UUID family, @Param("now") Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now"
          + " where t.userId = :user and t.revokedAt is null")
  int revokeAllOfUser(@Param("user") UUID user, @Param("now") Instant now);
}

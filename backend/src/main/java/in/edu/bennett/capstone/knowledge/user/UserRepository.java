package in.edu.bennett.capstone.knowledge.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Package-private: other modules go through {@link UserService} (specification 4.3). */
interface UserRepository
    extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByRole(Role role);

  /**
   * Locks the enabled admins' rows until the transaction ends (AUTH-2). A second request that could
   * remove an admin waits here, and PostgreSQL then re-checks the condition against the committed
   * rows, so it sees the first request's change.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.role = ADMIN and u.enabled = true")
  List<UserAccount> lockEnabledAdmins();
}

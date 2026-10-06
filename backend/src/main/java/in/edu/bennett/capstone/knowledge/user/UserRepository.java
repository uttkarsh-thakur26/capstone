package in.edu.bennett.capstone.knowledge.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: other modules go through {@link UserService} (specification 4.3). */
interface UserRepository extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByRole(Role role);
}

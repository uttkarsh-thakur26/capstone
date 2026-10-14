package in.edu.bennett.capstone.knowledge.user;

import static in.edu.bennett.capstone.knowledge.TestAccounts.PASSWORD;
import static in.edu.bennett.capstone.knowledge.TestAccounts.registerStudent;
import static org.assertj.core.api.Assertions.assertThat;

import in.edu.bennett.capstone.knowledge.IntegrationTest;
import in.edu.bennett.capstone.knowledge.TestAccounts;
import in.edu.bennett.capstone.knowledge.TestAccounts.Account;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An admin's change to an account survives a request of that account's own user that read the row
 * before the change and writes it afterwards (AUTH-2, AUTH-7). Each test opens the user's
 * transaction, lets an admin change the account in a transaction of its own that commits first, and
 * then commits the user's. If every column were written back, the user's transaction would restore
 * the role or enabled flag it read at its start, while the audit log recorded the admin's change.
 */
@IntegrationTest
class ConcurrentAccountChangesTest {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcClient jdbc;

  @Autowired UserService users;

  @Autowired PasswordEncoder passwordEncoder;

  @Autowired TransactionTemplate transactions;

  @Autowired PlatformTransactionManager transactionManager;

  private UUID adminId;

  @BeforeEach
  void findTheBootstrapAdmin() {
    adminId =
        jdbc.sql("SELECT id FROM app_user WHERE email = :email")
            .param("email", TestAccounts.ADMIN_EMAIL)
            .query(UUID.class)
            .single();
  }

  @Test
  void signingInKeepsAnAdminsDisableMadeMeanwhile() {
    Account student = registerStudent(mvc);

    // The sign-in transaction has read the account; its write happens when it commits.
    transactions.executeWithoutResult(
        status -> {
          users.recordLogin(student.id());
          adminCommits(student.id(), new UpdateUserRequest(null, false));
        });

    assertThat(column(student.id(), "enabled")).isEqualTo("false");
    assertThat(column(student.id(), "last_login_at")).isNotNull();
  }

  @Test
  void changingThePasswordKeepsAnAdminsDemotionMadeMeanwhile() {
    Account faculty = registerStudent(mvc);
    jdbc.sql("UPDATE app_user SET role = 'FACULTY' WHERE id = :id")
        .param("id", faculty.id())
        .update();

    // The password change reads the account, then spends two Argon2 hashes before it writes.
    transactions.executeWithoutResult(
        status -> {
          users.changePassword(faculty.id(), PASSWORD, "a brand new passphrase");
          adminCommits(faculty.id(), new UpdateUserRequest(Role.STUDENT, null));
        });

    assertThat(column(faculty.id(), "role")).isEqualTo("STUDENT");
    assertThat(
            passwordEncoder.matches(
                "a brand new passphrase", column(faculty.id(), "password_hash")))
        .isTrue();
  }

  /** The admin's request: a transaction of its own, committed while the user's is still open. */
  private void adminCommits(UUID userId, UpdateUserRequest change) {
    TransactionTemplate separate = new TransactionTemplate(transactionManager);
    separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    // Set here, not before the test: each MockMvc request clears the security context.
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    try {
      separate.executeWithoutResult(status -> users.updateUser(adminId, userId, change));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String column(UUID id, String column) {
    return jdbc.sql("SELECT " + column + "::text FROM app_user WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }
}

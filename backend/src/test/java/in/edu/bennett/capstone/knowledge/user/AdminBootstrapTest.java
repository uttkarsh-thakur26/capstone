package in.edu.bennett.capstone.knowledge.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import in.edu.bennett.capstone.knowledge.AuditRows;
import in.edu.bennett.capstone.knowledge.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-3: the first admin comes from configuration, only while no admin exists. Tests that need a
 * database without an admin demote the existing ones inside a transaction that is rolled back.
 */
@IntegrationTest
class AdminBootstrapTest {

  private static final String CONFIGURED_EMAIL = "admin@test.invalid";

  @Autowired UserService users;

  @Autowired JdbcClient jdbc;

  @Autowired PasswordEncoder passwordEncoder;

  @Test
  void startupCreatedTheConfiguredAdmin() {
    assertThat(column(CONFIGURED_EMAIL, "role")).isEqualTo("ADMIN");
    assertThat(column(CONFIGURED_EMAIL, "enabled")).isEqualTo("true");
    assertThat(
            passwordEncoder.matches(
                "test-only-admin-password", column(CONFIGURED_EMAIL, "password_hash")))
        .isTrue();
  }

  @Test
  void nothingHappensWhileAnAdminExists() {
    long admins = countAdmins();

    users.bootstrapAdmin(
        new BootstrapAdminProperties("second@test.invalid", "another-password", "Second"));

    assertThat(countAdmins()).isEqualTo(admins);
    assertThat(countByEmail("second@test.invalid")).isZero();
  }

  @Test
  @Transactional
  void createsTheAdminWhenNoneExists() {
    demoteAllAdmins();

    users.bootstrapAdmin(
        new BootstrapAdminProperties("  New.Admin@Test.Invalid ", "a-long-enough-password", "New"));

    assertThat(column("new.admin@test.invalid", "role")).isEqualTo("ADMIN");
    String id = column("new.admin@test.invalid", "id");
    assertThat(AuditRows.forResource(jdbc, id))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("action", "USER_REGISTERED");
              assertThat(row.get("actor_user_id")).isNull();
              assertThat((String) row.get("details")).contains("bootstrap");
            });
  }

  @Test
  @Transactional
  void withoutConfigurationAndWithoutAnAdminStartupFails() {
    demoteAllAdmins();

    assertThatIllegalStateException()
        .isThrownBy(
            () -> users.bootstrapAdmin(new BootstrapAdminProperties(null, null, "Administrator")))
        .withMessageContaining("APP_BOOTSTRAP_ADMIN_EMAIL");
  }

  @Test
  @Transactional
  void anExistingAccountIsNeverPromoted() {
    demoteAllAdmins();

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                users.bootstrapAdmin(
                    new BootstrapAdminProperties(CONFIGURED_EMAIL, "a-long-enough-password", "X")))
        .withMessageContaining("not promoted");
    assertThat(column(CONFIGURED_EMAIL, "role")).isEqualTo("STUDENT");
  }

  @Test
  @Transactional
  void aTooShortPasswordStopsStartupWithoutPrintingIt() {
    demoteAllAdmins();

    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                users.bootstrapAdmin(
                    new BootstrapAdminProperties("short@test.invalid", "sh0rt", "X")))
        .withMessageContaining("10 to 128")
        .withMessageNotContaining("sh0rt");
  }

  @Test
  void propertiesNeverPrintThePassword() {
    BootstrapAdminProperties properties =
        new BootstrapAdminProperties("a@test.invalid", "do-not-print-me", "A");

    assertThat(properties.toString()).doesNotContain("do-not-print-me").contains("<set>");
  }

  private void demoteAllAdmins() {
    jdbc.sql("UPDATE app_user SET role = 'STUDENT' WHERE role = 'ADMIN'").update();
  }

  private long countAdmins() {
    return jdbc.sql("SELECT count(*) FROM app_user WHERE role = 'ADMIN'")
        .query(Long.class)
        .single();
  }

  private long countByEmail(String email) {
    return jdbc.sql("SELECT count(*) FROM app_user WHERE email = :email")
        .param("email", email)
        .query(Long.class)
        .single();
  }

  private String column(String email, String column) {
    return jdbc.sql("SELECT " + column + "::text FROM app_user WHERE email = :email")
        .param("email", email)
        .query(String.class)
        .single();
  }
}

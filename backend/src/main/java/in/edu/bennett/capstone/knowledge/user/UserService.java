package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts and roles: the {@code user} module's API for the rest of the application (4.3). */
@Service
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final RegistrationProperties registration;

  UserService(
      UserRepository users, PasswordEncoder passwordEncoder, RegistrationProperties registration) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.registration = registration;
  }

  /** AUTH-1: self-registration always creates an enabled STUDENT. */
  @Transactional
  public UserResponse register(RegistrationRequest request) {
    if (!registration.allows(request.email())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "EMAIL_DOMAIN_NOT_ALLOWED",
          "Registration is not open for this email domain.");
    }
    if (users.existsByEmail(request.email())) {
      throw emailTaken();
    }
    UserAccount user =
        new UserAccount(
            request.email(),
            request.displayName(),
            passwordEncoder.encode(request.password()),
            Role.STUDENT);
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // Two registrations with one email at the same moment: the unique key decides.
      throw emailTaken();
    }
    log.info("Registered user {}", user.getId());
    return UserResponse.of(user);
  }

  /**
   * AUTH-3: creates the first admin from configuration, and only while no admin exists. Fails
   * instead of guessing when the configuration is missing or would promote an existing account.
   */
  @Transactional
  public void bootstrapAdmin(BootstrapAdminProperties admin) {
    if (users.existsByRole(Role.ADMIN)) {
      return;
    }
    if (admin.email() == null || admin.password() == null) {
      throw new IllegalStateException(
          "No admin account exists. Set APP_BOOTSTRAP_ADMIN_EMAIL and"
              + " APP_BOOTSTRAP_ADMIN_PASSWORD to create the first one (AUTH-3).");
    }
    if (!ValidPassword.Validator.isValidLength(admin.password())) {
      throw new IllegalStateException(
          "APP_BOOTSTRAP_ADMIN_PASSWORD must be 10 to 128 characters long (AUTH-4).");
    }
    if (users.existsByEmail(admin.email())) {
      // Promoting a self-registered account would hand admin rights to whoever registered it.
      throw new IllegalStateException(
          "No admin account exists, and APP_BOOTSTRAP_ADMIN_EMAIL belongs to an existing account."
              + " It is not promoted; choose an email that has no account.");
    }
    UserAccount user =
        new UserAccount(
            admin.email(),
            admin.displayName(),
            passwordEncoder.encode(admin.password()),
            Role.ADMIN);
    users.saveAndFlush(user);
    log.info("Created the first admin account {}", user.getId());
  }

  private static ApiException emailTaken() {
    return new ApiException(
        HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists.");
  }
}

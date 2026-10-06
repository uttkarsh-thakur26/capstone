package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import java.util.Map;
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

  /** The {@code resource_type} of audit entries about accounts. */
  public static final String RESOURCE_USER = "USER";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final RegistrationProperties registration;
  private final AuditService audit;

  UserService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      RegistrationProperties registration,
      AuditService audit) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.registration = registration;
    this.audit = audit;
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
    audit.record(
        AuditEvent.of(
                AuditAction.USER_REGISTERED, AuditOutcome.SUCCESS, user.getId(), user.getEmail())
            .withResource(RESOURCE_USER, user.getId()));
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
    // Nobody is signed in at startup, so the entry has no actor.
    audit.record(
        AuditEvent.of(AuditAction.USER_REGISTERED, AuditOutcome.SUCCESS, null, null)
            .withResource(RESOURCE_USER, user.getId())
            .withDetails(Map.of("source", "bootstrap", "role", Role.ADMIN.name())));
    log.info("Created the first admin account {}", user.getId());
  }

  private static ApiException emailTaken() {
    return new ApiException(
        HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists.");
  }
}

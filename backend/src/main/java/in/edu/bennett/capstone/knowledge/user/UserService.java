package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  private final Clock clock;

  UserService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      RegistrationProperties registration,
      AuditService audit,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.registration = registration;
    this.audit = audit;
    this.clock = clock;
  }

  /** For the password check at sign-in. Expects a normalized email. */
  @Transactional(readOnly = true)
  public Optional<UserCredentials> findCredentials(String email) {
    return users
        .findByEmail(email)
        .map(
            user ->
                new UserCredentials(
                    user.getId(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.getRole(),
                    user.isEnabled()));
  }

  /**
   * The account behind a request, read on every request and never cached, so that a changed role or
   * a disabled account applies at once (AUTH-7, ACL-7). Empty for a disabled or missing user.
   */
  @Transactional(readOnly = true)
  public Optional<UserResponse> findEnabled(UUID id) {
    return users.findById(id).filter(UserAccount::isEnabled).map(UserResponse::of);
  }

  /** Any account, enabled or not, for example to name the actor of an audit entry. */
  @Transactional(readOnly = true)
  public Optional<UserResponse> find(UUID id) {
    return users.findById(id).map(UserResponse::of);
  }

  @Transactional
  public void recordLogin(UUID id) {
    users.findById(id).ifPresent(user -> user.recordLogin(clock.instant()));
  }

  /**
   * Changes the user's own password after checking the current one (S2, ADR-025). Revoking the
   * user's sessions is the caller's part, because refresh tokens belong to the security module.
   */
  @Transactional
  public void changePassword(UUID id, String currentPassword, String newPassword) {
    UserAccount user =
        users
            .findById(id)
            .orElseThrow(() -> new IllegalStateException("Signed-in user " + id + " not found"));
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      audit.recordSeparately(
          AuditEvent.of(
                  AuditAction.PASSWORD_CHANGED, AuditOutcome.DENIED, user.getId(), user.getEmail())
              .withResource(RESOURCE_USER, user.getId()));
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "CURRENT_PASSWORD_INCORRECT",
          "The current password is not correct.");
    }
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    audit.record(
        AuditEvent.of(
                AuditAction.PASSWORD_CHANGED, AuditOutcome.SUCCESS, user.getId(), user.getEmail())
            .withResource(RESOURCE_USER, user.getId()));
    log.info("User {} changed their password", user.getId());
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

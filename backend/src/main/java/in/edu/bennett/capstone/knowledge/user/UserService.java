package in.edu.bennett.capstone.knowledge.user;

import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.common.PageResponse;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

  /**
   * Names and emails of the given accounts, enabled or not, in one query: for example the owners of
   * a page of documents, or the people a document is shared with. Unknown ids are left out.
   */
  @Transactional(readOnly = true)
  public Map<UUID, UserSummary> findSummaries(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return users.findAllById(Set.copyOf(ids)).stream()
        .collect(Collectors.toMap(UserAccount::getId, UserSummary::of));
  }

  /**
   * DOC-8: finds enabled users to share a document with, by part of the name or the email. Faculty
   * and admins only, by URL rule and by method security. At least three characters are needed, so
   * the search cannot list every account.
   */
  @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
  @Transactional(readOnly = true)
  public PageResponse<UserSummary> searchUsers(String text, int page, int size) {
    if (text == null || text.strip().codePointCount(0, text.strip().length()) < 3) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "QUERY_TOO_SHORT", "Type at least three characters to search.");
    }
    return PageResponse.of(
        users
            .findAll(
                new UserQuery(text, null, true).toSpecification(),
                PageRequest.of(page, size, Sort.by("displayName", "email", "id")))
            .map(UserSummary::of));
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

  /** Columns the admin user list may be sorted by: API name to entity property. */
  private static final Map<String, String> SORTABLE =
      Map.of(
          "email", "email",
          "displayName", "displayName",
          "role", "role",
          "createdAt", "createdAt",
          "lastLoginAt", "lastLoginAt");

  /** 14.1: list and search users. Admins only, by method security as well as by URL (AUTH-8). */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public PageResponse<AdminUserResponse> listUsers(
      UserQuery query, int page, int size, String sort) {
    return PageResponse.of(
        users
            .findAll(
                query.toSpecification(),
                PageResponse.request(page, size, sort, SORTABLE, Sort.by("email")))
            .map(AdminUserResponse::of));
  }

  /**
   * AUTH-2: changes a role, the enabled flag, or both. The last enabled admin can be neither
   * demoted nor disabled. The enabled admins' rows are locked first, so two admins demoting each
   * other at the same moment are handled one after the other and the second is refused.
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public AdminUserResponse updateUser(UUID actorId, UUID userId, UpdateUserRequest change) {
    List<UserAccount> enabledAdmins = users.lockEnabledAdmins();
    // Read after the lock, so a change committed by a request we waited for is seen.
    UserAccount user =
        users
            .findById(userId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "The requested resource does not exist."));
    String actorEmail = users.findById(actorId).map(UserAccount::getEmail).orElse(null);
    Role newRole = change.role() == null ? user.getRole() : change.role();
    boolean newEnabled = change.enabled() == null ? user.isEnabled() : change.enabled();

    boolean removesAnAdmin = enabledAdmins.contains(user) && (newRole != Role.ADMIN || !newEnabled);
    if (removesAnAdmin && enabledAdmins.size() == 1) {
      AuditAction action =
          newRole != Role.ADMIN ? AuditAction.ROLE_CHANGED : AuditAction.USER_DISABLED;
      audit.recordSeparately(
          AuditEvent.of(action, AuditOutcome.FAILURE, actorId, actorEmail)
              .withResource(RESOURCE_USER, user.getId())
              .withDetails(Map.of("reason", "last enabled admin")));
      throw new ApiException(
          HttpStatus.CONFLICT,
          "LAST_ADMIN",
          "The last enabled admin cannot be demoted or disabled.");
    }

    if (newRole != user.getRole()) {
      Map<String, Object> details = new HashMap<>();
      details.put("from", user.getRole().name());
      details.put("to", newRole.name());
      user.changeRole(newRole);
      audit.record(
          AuditEvent.of(AuditAction.ROLE_CHANGED, AuditOutcome.SUCCESS, actorId, actorEmail)
              .withResource(RESOURCE_USER, user.getId())
              .withDetails(details));
    }
    if (newEnabled != user.isEnabled()) {
      user.setEnabled(newEnabled);
      audit.record(
          AuditEvent.of(
                  newEnabled ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                  AuditOutcome.SUCCESS,
                  actorId,
                  actorEmail)
              .withResource(RESOURCE_USER, user.getId()));
    }
    users.flush();
    log.info("Admin {} updated user {}", actorId, user.getId());
    return AdminUserResponse.of(user);
  }

  private static ApiException emailTaken() {
    return new ApiException(
        HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists.");
  }
}

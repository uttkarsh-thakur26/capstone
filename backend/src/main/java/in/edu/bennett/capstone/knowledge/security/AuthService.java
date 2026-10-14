package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.security.AccessTokenService.AccessToken;
import in.edu.bennett.capstone.knowledge.security.RefreshTokenService.IssuedToken;
import in.edu.bennett.capstone.knowledge.user.UserResponse;
import in.edu.bennett.capstone.knowledge.user.UserService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Sign-in, session refresh and sign-out (AUTH-5, AUTH-6, ADR-020). */
@Service
class AuthService {

  private final PasswordAuthenticator authenticator;
  private final UserService users;
  private final AccessTokenService accessTokens;
  private final RefreshTokenService refreshTokens;
  private final AuditService audit;
  private final TransactionTemplate transactions;

  AuthService(
      PasswordAuthenticator authenticator,
      UserService users,
      AccessTokenService accessTokens,
      RefreshTokenService refreshTokens,
      AuditService audit,
      TransactionTemplate transactions) {
    this.authenticator = authenticator;
    this.users = users;
    this.accessTokens = accessTokens;
    this.refreshTokens = refreshTokens;
    this.audit = audit;
    this.transactions = transactions;
  }

  /**
   * Every failure, whatever its reason, gives the same response, so that it does not reveal whether
   * the email has an account (AUTH-5). The reason is kept in the audit entry only.
   */
  Session login(String email, String password) {
    UUID id;
    try {
      id = authenticator.authenticate(email, password);
    } catch (AuthenticationException e) {
      audit.recordSeparately(
          AuditEvent.of(AuditAction.LOGIN_FAILURE, AuditOutcome.DENIED, null, email)
              .withDetails(
                  Map.of("reason", e instanceof DisabledException ? "disabled" : "credentials")));
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "The email or password is not correct.");
    }
    // The sign-in time, the new refresh token and the audit entry are committed together.
    return transactions.execute(
        status -> {
          users.recordLogin(id);
          UserResponse user = users.find(id).orElseThrow();
          IssuedToken refresh = refreshTokens.issueForNewSignIn(id);
          audit.record(
              AuditEvent.of(AuditAction.LOGIN_SUCCESS, AuditOutcome.SUCCESS, id, user.email())
                  .withResource(UserService.RESOURCE_USER, id));
          return new Session(user, accessTokens.issue(id), refresh);
        });
  }

  /** A new access token and a rotated refresh token, or empty if the session has ended. */
  Optional<Session> refresh(String rawRefreshToken) {
    return refreshTokens
        .rotate(rawRefreshToken)
        .map(
            rotation ->
                new Session(
                    rotation.user(), accessTokens.issue(rotation.user().id()), rotation.token()));
  }

  /** Ends the sign-in that the cookie belongs to. Works with an expired access token (ADR-020). */
  void logout(String rawRefreshToken) {
    transactions.executeWithoutResult(
        status ->
            refreshTokens
                .revokeSignIn(rawRefreshToken)
                .ifPresent(
                    id ->
                        audit.record(
                            AuditEvent.of(
                                    AuditAction.LOGOUT,
                                    AuditOutcome.SUCCESS,
                                    id,
                                    users.find(id).map(UserResponse::email).orElse(null))
                                .withResource(UserService.RESOURCE_USER, id))));
  }

  /** Changes the password and ends every session of the user, including this one (S2). */
  void changePassword(CurrentUser user, String currentPassword, String newPassword) {
    transactions.executeWithoutResult(
        status -> {
          users.changePassword(user.id(), currentPassword, newPassword);
          refreshTokens.revokeAllOfUser(user.id());
        });
  }

  record Session(UserResponse user, AccessToken accessToken, IssuedToken refreshToken) {}
}

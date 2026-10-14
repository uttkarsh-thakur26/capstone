package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.common.ApiException;
import in.edu.bennett.capstone.knowledge.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in, refresh, sign-out and the current user (specification 15.2, AUTH-5 to AUTH-9). */
@RestController
class AuthController {

  private final AuthService auth;
  private final RefreshCookies cookies;
  private final OriginCheck originCheck;

  AuthController(AuthService auth, RefreshCookies cookies, OriginCheck originCheck) {
    this.auth = auth;
    this.cookies = cookies;
    this.originCheck = originCheck;
  }

  @PostMapping(
      path = "/api/v1/auth/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
    return started(auth.login(request.email(), request.password()));
  }

  /** Authorized by the cookie alone, so it works after the access token expired (UI-1). */
  @PostMapping(path = "/api/v1/auth/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SessionResponse> refresh(
      @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
      HttpServletRequest request,
      HttpServletResponse response) {
    originCheck.verify(request);
    return auth.refresh(refreshToken)
        .map(this::started)
        .orElseThrow(
            () -> {
              // A token that no longer works is removed from the browser too.
              response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
              return new ApiException(
                  HttpStatus.UNAUTHORIZED,
                  "SESSION_EXPIRED",
                  "The session has ended. Sign in again.");
            });
  }

  /** Authorized by the cookie and the Origin check, not by the access token (ADR-020). */
  @PostMapping("/api/v1/auth/logout")
  ResponseEntity<Void> logout(
      @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
      HttpServletRequest request) {
    originCheck.verify(request);
    auth.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  @GetMapping(path = "/api/v1/auth/me", produces = MediaType.APPLICATION_JSON_VALUE)
  UserResponse me(@AuthenticationPrincipal CurrentUser user) {
    return user.toResponse();
  }

  /** Ends every session of the user, so the SPA signs in again with the new password. */
  @PutMapping(path = "/api/v1/auth/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody ChangePasswordRequest request) {
    auth.changePassword(user, request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  private ResponseEntity<SessionResponse> started(AuthService.Session session) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            cookies
                .create(session.refreshToken().value(), session.refreshToken().expiresAt())
                .toString())
        .body(SessionResponse.of(session));
  }
}

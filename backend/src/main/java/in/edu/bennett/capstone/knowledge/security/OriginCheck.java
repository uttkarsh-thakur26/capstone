package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AUTH-9: the cookie-authorized endpoints (refresh, logout) refuse requests from other origins.
 * Browsers send {@code Origin} on every cross-origin POST, so a missing header means a client that
 * is not a browser, which cannot be tricked into sending the cookie. {@code SameSite=Strict} is the
 * first defense; this check is the second.
 */
@Component
class OriginCheck {

  private final Set<String> allowed;

  OriginCheck(SecurityProperties properties) {
    this.allowed = Set.copyOf(properties.allowedOrigins());
  }

  void verify(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null && !allowed.contains(origin)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "ORIGIN_NOT_ALLOWED",
          "This request is not allowed from that origin.");
    }
  }
}

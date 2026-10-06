package in.edu.bennett.capstone.knowledge.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Turns the filter chain's {@code 401} and {@code 403} into Problem Details by handing the
 * exception to Spring MVC's exception resolvers, so the mapping stays in {@code
 * ApiExceptionHandler} (ADR-023).
 */
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final HandlerExceptionResolver resolver;

  ProblemDetailsSecurityHandler(HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    // RFC 6750: a 401 for a bearer-token API names the scheme. No error details are added.
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    resolve(request, response, authException, HttpStatus.UNAUTHORIZED);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    resolve(request, response, accessDeniedException, HttpStatus.FORBIDDEN);
  }

  private void resolve(
      HttpServletRequest request, HttpServletResponse response, Exception ex, HttpStatus status)
      throws IOException {
    ModelAndView handled = resolver.resolveException(request, response, null, ex);
    if (handled == null && !response.isCommitted()) {
      response.sendError(status.value());
    }
  }
}

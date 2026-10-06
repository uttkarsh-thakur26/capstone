package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.audit.AuditAction;
import in.edu.bennett.capstone.knowledge.audit.AuditEvent;
import in.edu.bennett.capstone.knowledge.audit.AuditOutcome;
import in.edu.bennett.capstone.knowledge.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Records {@code ACCESS_DENIED} when a role rule refuses an authenticated user, which is a {@code
 * 403} (ADR-024). Spring Security publishes the event for URL rules and for method security alike.
 * A refused anonymous request is a {@code 401} and is not recorded.
 */
@Component
class AccessDeniedAuditor {

  private final AuditService audit;
  private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

  AccessDeniedAuditor(AuditService audit) {
    this.audit = audit;
  }

  @EventListener
  void onDenied(AuthorizationDeniedEvent<?> event) {
    Authentication authentication = event.getAuthentication().get();
    if (!trust.isAuthenticated(authentication)) {
      return;
    }
    AuditEvent entry =
        authentication.getPrincipal() instanceof CurrentUser user
            ? AuditEvent.of(AuditAction.ACCESS_DENIED, AuditOutcome.DENIED, user.id(), user.email())
            : AuditEvent.of(
                AuditAction.ACCESS_DENIED, AuditOutcome.DENIED, null, authentication.getName());
    // The entry is written in its own transaction: the refused request may roll back its own.
    audit.recordSeparately(withResource(entry, event.getObject()));
  }

  private static AuditEvent withResource(AuditEvent entry, Object target) {
    if (target instanceof HttpServletRequest request) {
      return entry.withResource("ENDPOINT", request.getMethod() + " " + request.getRequestURI());
    }
    if (target instanceof MethodInvocation invocation) {
      return entry.withResource(
          "METHOD",
          invocation.getMethod().getDeclaringClass().getSimpleName()
              + "."
              + invocation.getMethod().getName());
    }
    return entry;
  }
}

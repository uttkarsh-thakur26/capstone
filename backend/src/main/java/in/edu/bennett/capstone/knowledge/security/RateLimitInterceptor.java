package in.edu.bennett.capstone.knowledge.security;

import in.edu.bennett.capstone.knowledge.common.RateLimited;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Checks the rate limit of an endpoint marked {@link RateLimited} before its controller method runs
 * (SEC-8): after authentication, so the user is known, and before the request body is read, so a
 * refused request does no work. The 429 is mapped to Problem Details like every other error. The
 * user comes from the authenticated principal only (ACL-E4); the address is the request's own,
 * which behind nginx is the client's (ADR-024).
 */
@Configuration(proxyBeanMethods = false)
class RateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

  private final ObjectProvider<RateLimiter> limiters;
  private RateLimiter limiter;

  /**
   * The limiter is looked up, not required: a web slice test (@WebMvcTest) loads every MVC
   * configuration but not this module's services, and then registers no interceptor. The whole
   * application always has the limiter; {@code RateLimitTest} shows the limits are active there.
   */
  RateLimitInterceptor(ObjectProvider<RateLimiter> limiters) {
    this.limiters = limiters;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    limiter = limiters.getIfAvailable();
    if (limiter != null) {
      registry.addInterceptor(this).addPathPatterns("/api/**");
    }
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    RateLimited limited = method.getMethodAnnotation(RateLimited.class);
    if (limited == null) {
      return true;
    }
    switch (limited.value()) {
      case AI -> {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
          limiter.checkAi(user.id());
        }
      }
      case REGISTRATION -> limiter.checkRegistration(request.getRemoteAddr());
    }
    return true;
  }
}

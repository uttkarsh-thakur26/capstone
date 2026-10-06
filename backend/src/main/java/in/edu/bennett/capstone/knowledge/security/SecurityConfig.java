package in.edu.bennett.capstone.knowledge.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Default deny (AUTH-8): only the endpoints on the public list answer without authentication.
 * Everything else needs a valid access token (AUTH-6), and admin endpoints are protected by URL
 * rules and by method security. Failures are Problem Details (ADR-023).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

  /** Public, or authorized by the refresh cookie: a bearer token sent to them is not read. */
  private static final Set<String> COOKIE_OR_PUBLIC_AUTH_PATHS =
      Set.of(
          "/api/v1/auth/register",
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout");

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
      JwtDecoder jwtDecoder,
      Converter<Jwt, AbstractAuthenticationToken> currentUserConverter)
      throws Exception {
    ProblemDetailsSecurityHandler problems = new ProblemDetailsSecurityHandler(resolver);
    http.authorizeHttpRequests(
            requests -> {
              // The container's error path renders Problem Details for requests that already
              // failed; denying it would replace their status with 401.
              requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
              requests.requestMatchers(HttpMethod.GET, "/api/v1/meta").permitAll();
              requests
                  .requestMatchers(
                      HttpMethod.POST, COOKIE_OR_PUBLIC_AUTH_PATHS.toArray(String[]::new))
                  .permitAll();
              requests.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
              if (apiDocsEnabled) {
                // API documentation is public in development only (AUTH-8, ADR-014).
                requests
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              // Admin endpoints also carry method security (AUTH-8).
              requests.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
              requests.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .bearerTokenResolver(bearerTokenResolver())
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(currentUserConverter))
                    .authenticationEntryPoint(problems)
                    .accessDeniedHandler(problems))
        // A stateless API with bearer tokens needs no CSRF protection. The cookie-authorized
        // refresh and logout rely on SameSite=Strict and an Origin check instead (AUTH-9).
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems));
    return http.build();
  }

  /** Makes Spring Security publish denials as events, which {@link AccessDeniedAuditor} records. */
  @Bean
  AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
    return new SpringAuthorizationEventPublisher(publisher);
  }

  /**
   * An expired access token that the SPA still sends to {@code /auth/refresh} must not turn the
   * refresh into a 401, so the auth endpoints never read the bearer header.
   */
  private static BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
    return (HttpServletRequest request) -> {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      return COOKIE_OR_PUBLIC_AUTH_PATHS.contains(path) ? null : header.resolve(request);
    };
  }
}
